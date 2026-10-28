import type { InterviewStage } from '@/api/interviews'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApplicationOffers } from '@/features/offers/ApplicationOffers'
import { DebriefPanel } from './DebriefPanel'
import { InterviewPrepPanel } from './InterviewPrepPanel'

/** Abas da candidatura em entrevista: a preparação, o registro pós-entrevista e as propostas. */
export function InterviewSection({ applicationId, jobId, status }: { applicationId: number; jobId: number; status: InterviewStage }) {
  return (
    <Tabs defaultValue={status === 'OFERTA' ? 'offers' : 'prep'}>
      <TabsList>
        <TabsTrigger value="prep">Preparação</TabsTrigger>
        <TabsTrigger value="debrief">Registro</TabsTrigger>
        <TabsTrigger value="offers">Propostas</TabsTrigger>
      </TabsList>
      <TabsContent value="prep" className="mt-3">
        <InterviewPrepPanel applicationId={applicationId} stage={status === 'TESTE_TECNICO' ? 'TESTE_TECNICO' : 'ENTREVISTA'} />
      </TabsContent>
      <TabsContent value="debrief" className="mt-3">
        <DebriefPanel applicationId={applicationId} jobId={jobId} stage={status} />
      </TabsContent>
      <TabsContent value="offers" className="mt-3">
        <ApplicationOffers applicationId={applicationId} />
      </TabsContent>
    </Tabs>
  )
}
