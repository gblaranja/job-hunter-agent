import { useApplicationContacts, useJobContacts } from '@/api/queries'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { UserPlus } from 'lucide-react'
import { ContactDialog } from './ContactDialog'
import { ContactList } from './ContactList'

/**
 * Quem você conhece na empresa da vaga: os contatos cadastrados e as conexões do LinkedIn. Com candidatura, inclui
 * quem já falou com você sobre ela.
 */
export function ContactsCard({ jobId, company, applicationId }: { jobId: number; company: string; applicationId?: number }) {
  const forApplication = useApplicationContacts(applicationId)
  const forJob = useJobContacts(jobId, applicationId === undefined)
  const query = applicationId !== undefined ? forApplication : forJob
  const contacts = (applicationId !== undefined ? forApplication.data?.contacts : forJob.data) ?? []
  const connections = contacts.filter((contact) => contact.role === 'CONEXAO').length

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Contatos na empresa</CardTitle>
        <CardDescription>
          {contacts.length === 0
            ? 'Ninguém cadastrado nesta empresa ainda.'
            : connections > 0
              ? `${connections === 1 ? '1 conexão' : `${connections} conexões`} do LinkedIn`
              : 'Quem você conhece aqui'}
        </CardDescription>
        <CardAction>
          <ContactDialog
            defaultCompany={company}
            trigger={
              <Button variant="outline" size="sm">
                <UserPlus /> Adicionar
              </Button>
            }
          />
        </CardAction>
      </CardHeader>
      <CardContent>
        {query.isLoading && <LoadingBlock lines={2} />}
        {query.error && <ErrorBlock error={query.error} />}
        {contacts.length > 0 && <ContactList contacts={contacts} applicationId={applicationId} />}
      </CardContent>
    </Card>
  )
}
