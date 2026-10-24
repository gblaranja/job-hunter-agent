import { Link } from 'react-router'
import { useJob, type BoardCard } from '@/api/queries'
import { AppliedJobCard } from '@/components/AppliedJobCard'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { ApplicationDetailsForm } from '@/features/applications/ApplicationDetailsForm'
import { FollowUpStatus } from '@/features/applications/FollowUpStatus'
import { ResponseDialog } from '@/features/applications/ResponseDialog'
import { Timeline } from '@/features/applications/Timeline'
import { ArrowUpRight } from 'lucide-react'

/** Painel lateral do kanban: a vaga aplicada, anotações e o histórico da candidatura. */
export function ApplicationSheet({ card, onClose }: { card: BoardCard; onClose: () => void }) {
  const job = useJob(card.jobId)
  const application = job.data?.application
  // O board só traz status de link quando a vaga já foi congelada na aplicação.
  const hasSnapshot = card.linkStatus != null

  return (
    <Sheet open onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>{card.title}</SheetTitle>
          <SheetDescription>{card.company}</SheetDescription>
        </SheetHeader>
        <div className="space-y-6 px-4 pb-8">
          <div className="flex items-center justify-between">
            <StatusBadge status={card.status} />
            <Button asChild variant="link" size="sm">
              <Link to={`/jobs/${card.jobId}`}>
                Abrir detalhe completo <ArrowUpRight />
              </Link>
            </Button>
          </div>
          {hasSnapshot && <AppliedJobCard applicationId={card.applicationId} />}
          {job.isLoading && <LoadingBlock lines={2} />}
          {job.error && <ErrorBlock error={job.error} />}
          {application && (
            <>
              <FollowUpStatus application={application} />
              <ApplicationDetailsForm
                key={`${application.id}-${application.nextActionAt ?? ''}`}
                applicationId={application.id}
                notes={application.notes}
                nextActionAt={application.nextActionAt}
                nextActionKind={application.nextActionKind}
              />
              <ResponseDialog applicationId={application.id} />
              <section>
                <h3 className="mb-3 text-sm font-semibold">Histórico</h3>
                <Timeline events={job.data?.timeline ?? []} />
              </section>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
