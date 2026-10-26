import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/PageHeader'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { OffersComparison } from '@/features/offers/OffersComparison'
import { StoriesTab } from './StoriesTab'

/** Entrevistas: o banco de histórias STAR e a comparação das propostas. */
export function InterviewsPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'offers' ? 'offers' : 'stories'
  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader title="Entrevistas" description="Suas histórias para as respostas e as propostas lado a lado." />
      <Tabs value={tab} onValueChange={(value) => setParams(value === 'offers' ? { tab: 'offers' } : {}, { replace: true })}>
        <TabsList>
          <TabsTrigger value="stories">Histórias</TabsTrigger>
          <TabsTrigger value="offers">Propostas</TabsTrigger>
        </TabsList>
        <TabsContent value="stories" className="mt-4">
          <StoriesTab />
        </TabsContent>
        <TabsContent value="offers" className="mt-4">
          <OffersComparison />
        </TabsContent>
      </Tabs>
    </div>
  )
}
