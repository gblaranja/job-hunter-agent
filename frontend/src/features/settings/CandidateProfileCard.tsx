import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import {
  useCandidateProfile,
  useSaveCandidateProfile,
  type CandidateProfile,
  type CandidateProfileCommand,
} from '@/api/queries'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { contractRegimeLabels, educationLevelLabels, englishLevelLabels } from '@/lib/labels'
import { UserRound } from 'lucide-react'

/** Valor do Select para "não informado" (o Radix não aceita valor vazio). */
const NONE = 'NAO_INFORMADO'

type EnglishLevel = NonNullable<CandidateProfileCommand['englishLevel']>
type EducationLevel = NonNullable<CandidateProfileCommand['educationLevel']>

/** Dados que o ATS usa como eliminatórios: comparados com cada vaga, limitam o score quando não batem. */
export function CandidateProfileCard() {
  const profile = useCandidateProfile()

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <UserRound className="size-4" aria-hidden /> Seus dados para a triagem
        </CardTitle>
        <CardDescription>
          O ATS corta primeiro por anos de experiência, inglês, local e regime. Com estes dados, a vaga mostra quais
          eliminatórios você atende; um eliminatório que não bate limita o score a 40. Campo vazio fica como sem
          informação.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {profile.isLoading && <LoadingBlock lines={3} />}
        {profile.error && <ErrorBlock error={profile.error} />}
        {/* A chave remonta o formulário quando os dados salvos mudam. */}
        {profile.data && <ProfileForm key={profile.data.updatedAt ?? 'novo'} profile={profile.data} />}
      </CardContent>
    </Card>
  )
}

const splitList = (value: string) =>
  value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

const toNumber = (value: string) => {
  const parsed = Number(value.replace(',', '.'))
  return value.trim() === '' || Number.isNaN(parsed) ? null : parsed
}

function ProfileForm({ profile }: { profile: CandidateProfile }) {
  const save = useSaveCandidateProfile()
  const [years, setYears] = useState(profile.yearsOfExperience?.toString() ?? '')
  const [english, setEnglish] = useState<string>(profile.englishLevel ?? NONE)
  const [city, setCity] = useState(profile.city ?? '')
  const [state, setState] = useState(profile.state ?? '')
  const [onsite, setOnsite] = useState(profile.maxOnsiteDays?.toString() ?? '')
  const [contracts, setContracts] = useState<string[]>(profile.acceptedContracts)
  const [authorizations, setAuthorizations] = useState(profile.workAuthorizations.join(', '))
  const [education, setEducation] = useState<string>(profile.educationLevel ?? NONE)
  const [course, setCourse] = useState(profile.education ?? '')
  const [certifications, setCertifications] = useState(profile.certifications.join(', '))
  const [salaryClt, setSalaryClt] = useState(profile.desiredSalaryClt?.toString() ?? '')
  const [salaryPj, setSalaryPj] = useState(profile.desiredSalaryPj?.toString() ?? '')
  const [weeklyGoal, setWeeklyGoal] = useState(profile.weeklyGoal.toString())

  const hasSuggestion =
    profile.suggestedFrom != null &&
    ((profile.suggestedYearsOfExperience != null && profile.suggestedYearsOfExperience !== profile.yearsOfExperience) ||
      (profile.suggestedEnglishLevel != null && profile.suggestedEnglishLevel !== profile.englishLevel))

  function applySuggestion() {
    if (profile.suggestedYearsOfExperience != null) setYears(profile.suggestedYearsOfExperience.toString())
    if (profile.suggestedEnglishLevel) setEnglish(profile.suggestedEnglishLevel)
  }

  function toggleContract(contract: string, checked: boolean) {
    setContracts((current) => (checked ? [...current, contract] : current.filter((item) => item !== contract)))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate(
      {
        yearsOfExperience: toNumber(years),
        englishLevel: english === NONE ? null : (english as EnglishLevel),
        city: city.trim() || null,
        state: state.trim() || null,
        maxOnsiteDays: toNumber(onsite),
        acceptedContracts: contracts,
        workAuthorizations: splitList(authorizations),
        educationLevel: education === NONE ? null : (education as EducationLevel),
        education: course.trim() || null,
        certifications: splitList(certifications),
        desiredSalaryClt: toNumber(salaryClt),
        desiredSalaryPj: toNumber(salaryPj),
        weeklyGoal: toNumber(weeklyGoal),
      },
      {
        onSuccess: () => toast.success('Dados salvos. As vagas abertas serão repontuadas.'),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <form onSubmit={submit} className="space-y-5">
      {hasSuggestion && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-dashed p-3 text-sm">
          <p>
            Lido do currículo <strong>{profile.suggestedFrom}</strong>:{' '}
            {[
              profile.suggestedYearsOfExperience != null && `${profile.suggestedYearsOfExperience} anos de experiência`,
              profile.suggestedEnglishLevel && `inglês ${englishLevelLabels[profile.suggestedEnglishLevel]?.toLowerCase()}`,
            ]
              .filter(Boolean)
              .join(' e ')}
            . Confira antes de salvar.
          </p>
          <Button type="button" variant="outline" size="sm" onClick={applySuggestion}>
            Usar sugestão
          </Button>
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-2">
          <Label htmlFor="candidate-years">Anos de experiência</Label>
          <Input id="candidate-years" inputMode="numeric" value={years} onChange={(e) => setYears(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-english">Inglês</Label>
          <Select value={english} onValueChange={setEnglish}>
            <SelectTrigger id="candidate-english" className="w-full" aria-label="Inglês">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>Não informado</SelectItem>
              {Object.entries(englishLevelLabels).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-onsite">Máximo de dias presenciais por semana</Label>
          <Input
            id="candidate-onsite"
            inputMode="numeric"
            placeholder="0 = só remoto"
            value={onsite}
            onChange={(e) => setOnsite(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-city">Cidade</Label>
          <Input id="candidate-city" value={city} onChange={(e) => setCity(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-state">UF</Label>
          <Input id="candidate-state" maxLength={2} value={state} onChange={(e) => setState(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-authorizations">Pode trabalhar em (países, ex.: BR, PT)</Label>
          <Input
            id="candidate-authorizations"
            value={authorizations}
            onChange={(e) => setAuthorizations(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-education">Formação</Label>
          <Select value={education} onValueChange={setEducation}>
            <SelectTrigger id="candidate-education" className="w-full" aria-label="Formação">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>Não informada</SelectItem>
              {Object.entries(educationLevelLabels).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-course">Curso</Label>
          <Input id="candidate-course" value={course} onChange={(e) => setCourse(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-certifications">Certificações (separadas por vírgula)</Label>
          <Input
            id="candidate-certifications"
            value={certifications}
            onChange={(e) => setCertifications(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-salary-clt">Pretensão CLT (R$ por mês, bruto)</Label>
          <Input
            id="candidate-salary-clt"
            inputMode="decimal"
            value={salaryClt}
            onChange={(e) => setSalaryClt(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-salary-pj">Pretensão PJ (R$ por mês)</Label>
          <Input id="candidate-salary-pj" inputMode="decimal" value={salaryPj} onChange={(e) => setSalaryPj(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="candidate-goal">Meta de candidaturas por semana</Label>
          <Input
            id="candidate-goal"
            inputMode="numeric"
            value={weeklyGoal}
            onChange={(e) => setWeeklyGoal(e.target.value)}
          />
        </div>
      </div>
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Regimes aceitos</legend>
        <div className="flex flex-wrap gap-6 text-sm">
          {Object.entries(contractRegimeLabels).map(([value, label]) => (
            <label key={value} className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={contracts.includes(value)}
                onChange={(e) => toggleContract(value, e.target.checked)}
              />{' '}
              {label}
            </label>
          ))}
        </div>
      </fieldset>
      <div className="flex justify-end">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Salvando…' : 'Salvar dados'}
        </Button>
      </div>
    </form>
  )
}
