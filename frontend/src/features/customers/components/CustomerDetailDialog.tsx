import {
  Calendar,
  Clock,
  DollarSign,
  Mail,
  MapPin,
  Phone,
  Receipt,
  ShoppingCart,
  TrendingUp,
  Users,
} from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useCustomer, useCustomerSalesHistory } from '../hooks/use-customers.ts'

interface CustomerDetailDialogProps {
  customerExternalId: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function CustomerDetailDialog({
  customerExternalId,
  open,
  onOpenChange,
}: CustomerDetailDialogProps) {
  const { t } = useTranslation()
  const customerQuery = useCustomer(
    customerExternalId ?? '',
    open && Boolean(customerExternalId),
  )
  const historyQuery = useCustomerSalesHistory(
    customerExternalId ?? '',
    { size: 50 },
    open && Boolean(customerExternalId),
  )

  const customer = customerQuery.data
  const salesHistory = historyQuery.data
  const sales = salesHistory?.content ?? []
  const aggregates = salesHistory?.aggregates ?? { salesCount: 0, totalAmount: 0 }
  const avgTicket =
    aggregates.salesCount > 0
      ? aggregates.totalAmount / aggregates.salesCount
      : 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center justify-between gap-4 mr-6">
            <div className="flex items-center gap-2.5">
              <div className="h-9 w-9 rounded-xl bg-sky-50 text-sky-700 flex items-center justify-center border border-sky-200">
                <Users className="h-5 w-5" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <span>{t('customers.detail.title')}</span>
                  {customer && (
                    <span className="text-sky-700 font-extrabold">
                      {customer.name}
                    </span>
                  )}
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500">
                  {customer?.createdAt && (
                    <span className="flex items-center gap-1 mt-0.5">
                      <Calendar className="h-3 w-3" />
                      {t('customers.detail.registeredAt')}:{' '}
                      {new Date(customer.createdAt).toLocaleString(undefined, {
                        dateStyle: 'medium',
                      })}
                    </span>
                  )}
                </DialogDescription>
              </div>
            </div>

            {customer && (
              <Badge
                variant="outline"
                className={`text-xs font-bold py-1 px-3 ${
                  customer.active
                    ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                    : 'bg-slate-100 text-slate-700 border-slate-300'
                }`}
              >
                {customer.active ? t('common.active') : t('common.inactive')}
              </Badge>
            )}
          </div>
        </DialogHeader>

        {customerQuery.isLoading ? (
          <div className="space-y-4 py-4">
            <Skeleton className="h-24 w-full rounded-xl" />
            <Skeleton className="h-32 w-full rounded-xl" />
            <Skeleton className="h-40 w-full rounded-xl" />
          </div>
        ) : customer ? (
          <div className="space-y-5 py-2">
            {/* Customer Master Info Card */}
            <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3">
              <div className="flex items-center justify-between border-b border-slate-200/80 pb-2">
                <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                  {t('customers.detail.infoTitle')}
                </span>
                {customer.updatedAt && (
                  <span className="text-[11px] text-slate-400 flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {t('customers.detail.lastUpdated')}:{' '}
                    {new Date(customer.updatedAt).toLocaleDateString()}
                  </span>
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                <div className="space-y-1">
                  <span className="text-[11px] font-semibold text-slate-400 block">
                    {t('customers.table.name')}
                  </span>
                  <p className="font-bold text-slate-900 text-sm">
                    {customer.name}
                  </p>
                  <p className="font-mono text-slate-600 text-[11px]">
                    {customer.taxId ? `NIT: ${customer.taxId}` : 'Sin NIT/RUC'}
                  </p>
                </div>

                <div className="space-y-1">
                  <span className="text-[11px] font-semibold text-slate-400 block">
                    {t('customers.detail.contactInfo')}
                  </span>
                  <div className="space-y-1 text-slate-700">
                    <div className="flex items-center gap-1.5">
                      <Mail className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                      <span>{customer.email || '—'}</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Phone className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                      <span>{customer.phone || '—'}</span>
                    </div>
                  </div>
                </div>

                <div className="space-y-1">
                  <span className="text-[11px] font-semibold text-slate-400 block">
                    {t('customers.table.address')}
                  </span>
                  <div className="flex items-start gap-1.5 text-slate-700">
                    <MapPin className="h-3.5 w-3.5 text-slate-400 shrink-0 mt-0.5" />
                    <span className="leading-snug">{customer.address || '—'}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Aggregates Summary Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Card className="border-slate-200 shadow-2xs">
                <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
                  <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                    {t('customers.detail.totalPurchases')}
                  </CardTitle>
                  <div className="h-7 w-7 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
                    <ShoppingCart className="h-4 w-4" />
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-black text-slate-900">
                    {aggregates.salesCount}
                  </div>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    {t('customers.detail.totalPurchasesDesc')}
                  </p>
                </CardContent>
              </Card>

              <Card className="border-slate-200 shadow-2xs">
                <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
                  <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                    {t('customers.detail.totalInvoiced')}
                  </CardTitle>
                  <div className="h-7 w-7 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                    <DollarSign className="h-4 w-4" />
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-black text-slate-900 font-mono">
                    ${aggregates.totalAmount.toFixed(2)}
                  </div>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    {t('customers.detail.totalInvoicedDesc')}
                  </p>
                </CardContent>
              </Card>

              <Card className="border-slate-200 shadow-2xs">
                <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
                  <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                    {t('customers.detail.avgTicket')}
                  </CardTitle>
                  <div className="h-7 w-7 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                    <TrendingUp className="h-4 w-4" />
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-black text-slate-900 font-mono">
                    ${avgTicket.toFixed(2)}
                  </div>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    {t('customers.detail.avgTicketDesc')}
                  </p>
                </CardContent>
              </Card>
            </div>

            {/* Sales Purchase History Table */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                  {t('customers.detail.salesHistoryTitle')}
                </h3>
                <span className="text-[11px] text-slate-500">
                  {t('customers.detail.salesHistoryDesc')}
                </span>
              </div>

              {historyQuery.isLoading ? (
                <div className="space-y-2 p-3 bg-white border border-slate-200 rounded-xl">
                  <Skeleton className="h-8 w-full rounded" />
                  <Skeleton className="h-8 w-full rounded" />
                  <Skeleton className="h-8 w-full rounded" />
                </div>
              ) : sales.length === 0 ? (
                <div className="py-8 px-4 border-2 border-dashed border-slate-200 rounded-xl text-center bg-slate-50/50 space-y-1.5">
                  <Receipt className="h-7 w-7 mx-auto text-slate-300" />
                  <p className="text-xs font-semibold text-slate-700">
                    {t('customers.detail.noSalesHistory')}
                  </p>
                </div>
              ) : (
                <div className="rounded-xl border border-slate-200 bg-white overflow-hidden shadow-2xs">
                  <Table>
                    <TableHeader className="bg-slate-50 border-b border-slate-200">
                      <TableRow>
                        <TableHead className="text-xs font-bold text-slate-700 h-9 px-3">
                          {t('sales.table.invoiceNumber')}
                        </TableHead>
                        <TableHead className="text-xs font-bold text-slate-700 h-9 px-3">
                          {t('sales.table.date')}
                        </TableHead>
                        <TableHead className="text-xs font-bold text-slate-700 h-9 px-3">
                          {t('sales.table.branch')}
                        </TableHead>
                        <TableHead className="text-xs font-bold text-slate-700 h-9 px-3">
                          {t('sales.table.soldBy')}
                        </TableHead>
                        <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                          {t('sales.table.total')}
                        </TableHead>
                        <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-center">
                          {t('sales.table.status')}
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {sales.map((sale) => (
                        <TableRow
                          key={sale.externalId}
                          className="border-b border-slate-100 last:border-0 text-xs hover:bg-slate-50/80"
                        >
                          <TableCell className="py-2.5 px-3 font-mono font-bold text-slate-900">
                            {sale.invoiceNumber}
                          </TableCell>
                          <TableCell className="py-2.5 px-3 text-slate-600">
                            {new Date(sale.createdAt).toLocaleString(undefined, {
                              dateStyle: 'short',
                              timeStyle: 'short',
                            })}
                          </TableCell>
                          <TableCell className="py-2.5 px-3 text-slate-700">
                            {sale.branch?.name ?? '—'}
                          </TableCell>
                          <TableCell className="py-2.5 px-3 text-slate-600">
                            {sale.soldBy?.username ?? '—'}
                          </TableCell>
                          <TableCell className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">
                            ${sale.totalAmount.toFixed(2)}
                          </TableCell>
                          <TableCell className="py-2.5 px-3 text-center">
                            <Badge
                              variant="outline"
                              className={`text-[10px] font-bold py-0.5 px-2 ${
                                sale.status === 'COMPLETED'
                                  ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                                  : 'bg-rose-50 text-rose-800 border-rose-300'
                              }`}
                            >
                              {sale.status === 'COMPLETED'
                                ? t('sales.status.COMPLETED')
                                : t('sales.status.CANCELLED')}
                            </Badge>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </div>
          </div>
        ) : null}

        <DialogFooter className="border-t border-slate-100 pt-3">
          <Button
            type="button"
            variant="default"
            size="sm"
            className="text-xs bg-slate-900 hover:bg-slate-800 text-white"
            onClick={() => onOpenChange(false)}
          >
            {t('common.close')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
