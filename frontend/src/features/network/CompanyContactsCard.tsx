import { useContacts } from '@/api/queries'
import { ErrorBlock, LoadingBlock } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { UserPlus } from 'lucide-react'
import { ContactDialog } from './ContactDialog'
import { ContactList } from './ContactList'

/** Quem você conhece na empresa: contatos cadastrados e conexões do LinkedIn. */
export function CompanyContactsCard({ companyId, company }: { companyId: number; company: string }) {
  const contacts = useContacts({ companyId, page: 0, size: 50 })
  const total = contacts.data?.total ?? 0

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Quem você conhece aqui</CardTitle>
        <CardDescription>
          {total === 0 ? 'Nenhum contato nem conexão nesta empresa.' : total === 1 ? '1 pessoa' : `${total} pessoas`}
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
        {contacts.isLoading && <LoadingBlock lines={2} />}
        {contacts.error && <ErrorBlock error={contacts.error} />}
        {contacts.data && contacts.data.items.length > 0 && <ContactList contacts={contacts.data.items} />}
      </CardContent>
    </Card>
  )
}
