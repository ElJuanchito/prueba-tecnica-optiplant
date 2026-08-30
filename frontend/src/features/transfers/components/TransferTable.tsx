import * as React from 'react'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import { useTransfers, useTransferDetail } from '../hooks/use-transfers.ts'
import { TransferDetailDialog } from './TransferDetailDialog.tsx'
import { TransferApprovalDialog } from './TransferApprovalDialog.tsx'
import { TransferDispatchDialog } from './TransferDispatchDialog.tsx'
import { TransferReceiptDialog } from './TransferReceiptDialog.tsx'
import { TransferCancelDialog } from './TransferCancelDialog.tsx'
import type {
  TransferDirection,
  TransferPriority,
  TransferQueryParams,
  TransferStatus,
  TransferSummaryResponse,
} from '../types/transfer.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  ArrowLeftRight,
  ArrowRight,
  Ban,
  CheckSquare,
  ChevronLeft,
  ChevronRight,
  Eye,
  Inbox,
  RefreshCw,
  Truck,
} from 'lucide-react'

export function TransferTable() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role ?? 'OPERATOR'
  const userBranchId = session?.branchId

  // Query and Filter state
  const [statusFilter, setStatusFilter] = React.useState<string>('ALL')
  const [directionFilter, setDirectionFilter] = React.useState<string>('ALL')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 10

  const queryParams: TransferQueryParams = {
    page,
    size: pageSize,
    status:
      statusFilter !== 'ALL' ? (statusFilter as TransferStatus) : undefined,
    direction:
      directionFilter !== 'ALL'
        ? (directionFilter as TransferDirection)
        : undefined,
  }

  const transfersQuery = useTransfers(queryParams)

  // Dialog states
  const [selectedDetailId, setSelectedDetailId] = React.useState<string | null>(
    null,
  )
  const [isDetailOpen, setIsDetailOpen] = React.useState(false)

  const [activeTransferForAction, setActiveTransferForAction] =
    React.useState<TransferSummaryResponse | null>(null)
  const [isApprovalOpen, setIsApprovalOpen] = React.useState(false)
  const [isDispatchOpen, setIsDispatchOpen] = React.useState(false)
  const [isReceiptOpen, setIsReceiptOpen] = React.useState(false)
  const [isCancelOpen, setIsCancelOpen] = React.useState(false)

  // Fetch full detail for action dialogs if needed
  const activeDetailQuery = useTransferDetail(
    activeTransferForAction?.externalId ?? '',
    Boolean(
      activeTransferForAction?.externalId &&
      (isApprovalOpen || isDispatchOpen || isReceiptOpen),
    ),
  )

  const handleOpenDetail = (id: string) => {
    setSelectedDetailId(id)
    setIsDetailOpen(true)
  }

  const handleOpenApprove = (transfer: TransferSummaryResponse) => {
    setActiveTransferForAction(transfer)
    setIsApprovalOpen(true)
  }

  const handleOpenDispatch = (transfer: TransferSummaryResponse) => {
    setActiveTransferForAction(transfer)
    setIsDispatchOpen(true)
  }

  const handleOpenReceipt = (transfer: TransferSummaryResponse) => {
    setActiveTransferForAction(transfer)
    setIsReceiptOpen(true)
  }

  const handleOpenCancel = (transfer: TransferSummaryResponse) => {
    setActiveTransferForAction(transfer)
    setIsCancelOpen(true)
  }

  const getStatusBadge = (status: TransferStatus) => {
    switch (status) {
      case 'REQUESTED':
        return (
          <Badge
            variant="outline"
            className="bg-sky-50 text-sky-700 border-sky-200 text-[10px]"
          >
            {t('transfers.statuses.REQUESTED')}
          </Badge>
        )
      case 'IN_PREPARATION':
        return (
          <Badge
            variant="outline"
            className="bg-amber-50 text-amber-700 border-amber-200 text-[10px]"
          >
            {t('transfers.statuses.IN_PREPARATION')}
          </Badge>
        )
      case 'IN_TRANSIT':
        return (
          <Badge
            variant="outline"
            className="bg-indigo-50 text-indigo-700 border-indigo-200 text-[10px]"
          >
            {t('transfers.statuses.IN_TRANSIT')}
          </Badge>
        )
      case 'RECEIVED':
        return (
          <Badge
            variant="outline"
            className="bg-emerald-50 text-emerald-700 border-emerald-200 text-[10px]"
          >
            {t('transfers.statuses.RECEIVED')}
          </Badge>
        )
      case 'RECEIVED_WITH_DISCREPANCY':
        return (
          <Badge
            variant="outline"
            className="bg-rose-50 text-rose-700 border-rose-200 text-[10px]"
          >
            {t('transfers.statuses.RECEIVED_WITH_DISCREPANCY')}
          </Badge>
        )
      case 'CANCELLED':
        return (
          <Badge
            variant="outline"
            className="bg-slate-100 text-slate-600 border-slate-200 text-[10px]"
          >
            {t('transfers.statuses.CANCELLED')}
          </Badge>
        )
      default:
        return (
          <Badge variant="outline" className="text-[10px]">
            {status}
          </Badge>
        )
    }
  }

  const getPriorityBadge = (priority: TransferPriority) => {
    switch (priority) {
      case 'URGENT':
        return (
          <Badge
            variant="outline"
            className="bg-red-50 text-red-700 border-red-200 font-semibold text-[10px]"
          >
            {t('transfers.priorities.URGENT')}
          </Badge>
        )
      case 'STANDARD':
        return (
          <Badge
            variant="outline"
            className="bg-slate-100 text-slate-700 border-slate-200 text-[10px]"
          >
            {t('transfers.priorities.STANDARD')}
          </Badge>
        )
      case 'LOW':
        return (
          <Badge
            variant="outline"
            className="bg-zinc-50 text-zinc-600 border-zinc-200 text-[10px]"
          >
            {t('transfers.priorities.LOW')}
          </Badge>
        )
      default:
        return (
          <Badge variant="outline" className="text-[10px]">
            {priority}
          </Badge>
        )
    }
  }

  const totalPages = transfersQuery.data
    ? Math.ceil(transfersQuery.data.totalElements / pageSize)
    : 1

  return (
    <div className="space-y-4">
      {/* Filters bar */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs">
        <div className="flex flex-wrap items-center gap-2.5 w-full sm:w-auto">
          {/* Status Filter */}
          <div className="w-44">
            <Select
              value={statusFilter}
              onValueChange={(val) => {
                setStatusFilter(val)
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-9 bg-slate-50 border-slate-200">
                <SelectValue placeholder="Estado" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL" className="text-xs">
                  {t('common.all')} ({t('transfers.status')})
                </SelectItem>
                <SelectItem value="REQUESTED" className="text-xs">
                  {t('transfers.statuses.REQUESTED')}
                </SelectItem>
                <SelectItem value="IN_PREPARATION" className="text-xs">
                  {t('transfers.statuses.IN_PREPARATION')}
                </SelectItem>
                <SelectItem value="IN_TRANSIT" className="text-xs">
                  {t('transfers.statuses.IN_TRANSIT')}
                </SelectItem>
                <SelectItem value="RECEIVED" className="text-xs">
                  {t('transfers.statuses.RECEIVED')}
                </SelectItem>
                <SelectItem
                  value="RECEIVED_WITH_DISCREPANCY"
                  className="text-xs"
                >
                  {t('transfers.statuses.RECEIVED_WITH_DISCREPANCY')}
                </SelectItem>
                <SelectItem value="CANCELLED" className="text-xs">
                  {t('transfers.statuses.CANCELLED')}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Direction Filter */}
          <div className="w-36">
            <Select
              value={directionFilter}
              onValueChange={(val) => {
                setDirectionFilter(val)
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-9 bg-slate-50 border-slate-200">
                <SelectValue placeholder="Dirección" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL" className="text-xs">
                  {t('common.all')} ({t('transfers.direction')})
                </SelectItem>
                <SelectItem value="INBOUND" className="text-xs">
                  {t('transfers.inbound')}
                </SelectItem>
                <SelectItem value="OUTBOUND" className="text-xs">
                  {t('transfers.outbound')}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={() => transfersQuery.refetch()}
            disabled={transfersQuery.isFetching}
            className="text-xs h-9 gap-1 text-slate-600"
            title={t('common.refresh')}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 ${
                transfersQuery.isFetching ? 'animate-spin' : ''
              }`}
            />
            <span className="hidden md:inline">{t('common.refresh')}</span>
          </Button>
        </div>

        {/* Count summary */}
        {transfersQuery.data && (
          <p className="text-xs text-slate-500 font-medium">
            {transfersQuery.data.totalElements} {t('common.results')}
          </p>
        )}
      </div>

      {/* Table Container */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-2xs overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50/80 border-b border-slate-200">
            <TableRow>
              <TableHead className="text-xs font-bold text-slate-700 py-3">
                {t('transfers.transferNumber')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('transfers.status')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('transfers.priority')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('transfers.originBranch')} →{' '}
                {t('transfers.destinationBranch')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('transfers.createdAt')} / {t('transfers.estimatedArrival')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right pr-4">
                {t('common.actions')}
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {transfersQuery.isLoading && (
              <>
                {[...Array(5)].map((_, i) => (
                  <TableRow key={i}>
                    <TableCell>
                      <Skeleton className="h-4 w-24" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-5 w-20" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-5 w-16" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-44" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-32" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-7 w-28 ml-auto" />
                    </TableCell>
                  </TableRow>
                ))}
              </>
            )}

            {!transfersQuery.isLoading &&
              transfersQuery.data?.content.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={6}
                    className="text-center py-12 text-slate-400"
                  >
                    <ArrowLeftRight className="h-8 w-8 mx-auto mb-2 text-slate-300" />
                    <p className="text-sm font-semibold">
                      {t('transfers.noTransfers')}
                    </p>
                  </TableCell>
                </TableRow>
              )}

            {!transfersQuery.isLoading &&
              transfersQuery.data?.content.map((transfer) => {
                const isOrigin =
                  role === 'ADMIN' ||
                  (userBranchId &&
                    transfer.originBranch?.externalId === userBranchId)
                const isDestination =
                  role === 'ADMIN' ||
                  (userBranchId &&
                    transfer.destinationBranch?.externalId === userBranchId)

                return (
                  <TableRow
                    key={transfer.externalId}
                    className="hover:bg-slate-50/60"
                  >
                    {/* Transfer Number */}
                    <TableCell className="font-mono text-xs font-bold text-slate-900">
                      {transfer.transferNumber}
                    </TableCell>

                    {/* Status Badge */}
                    <TableCell>{getStatusBadge(transfer.status)}</TableCell>

                    {/* Priority Badge */}
                    <TableCell>{getPriorityBadge(transfer.priority)}</TableCell>

                    {/* Origin -> Destination */}
                    <TableCell className="text-xs">
                      <div className="flex items-center gap-1.5 text-slate-700">
                        <span className="font-medium text-slate-900 truncate max-w-[140px]">
                          {transfer.originBranch?.name ?? '—'}
                        </span>
                        <ArrowRight className="h-3 w-3 text-slate-400 shrink-0" />
                        <span className="font-medium text-slate-900 truncate max-w-[140px]">
                          {transfer.destinationBranch?.name ?? '—'}
                        </span>
                      </div>
                    </TableCell>

                    {/* CreatedAt / ETA */}
                    <TableCell className="text-xs text-slate-500">
                      <div>
                        {new Date(transfer.createdAt).toLocaleDateString()}
                      </div>
                      {transfer.estimatedArrivalAt && (
                        <div className="text-[10px] text-slate-400">
                          ETA:{' '}
                          {new Date(
                            transfer.estimatedArrivalAt,
                          ).toLocaleDateString()}
                        </div>
                      )}
                    </TableCell>

                    {/* Actions */}
                    <TableCell className="text-right pr-4">
                      <div className="flex items-center justify-end gap-1.5">
                        {/* View Detail */}
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleOpenDetail(transfer.externalId)}
                          className="h-7 px-2 text-xs text-slate-600 hover:text-slate-900"
                          title={t('transfers.actions.viewDetails')}
                        >
                          <Eye className="h-3.5 w-3.5 mr-1" />
                          <span className="hidden lg:inline">
                            {t('transfers.actions.viewDetails')}
                          </span>
                        </Button>

                        {/* Approve / Reject (REQUESTED & Origin & Manager/Admin) */}
                        {transfer.status === 'REQUESTED' &&
                          isOrigin &&
                          Permissions.canReviewTransfer(role) && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenApprove(transfer)}
                              className="h-7 px-2 text-xs bg-indigo-50 text-indigo-700 border-indigo-200 hover:bg-indigo-100 font-semibold"
                            >
                              <CheckSquare className="h-3.5 w-3.5 mr-1" />
                              {t('transfers.actions.approve')}
                            </Button>
                          )}

                        {/* Dispatch (IN_PREPARATION & Origin) */}
                        {transfer.status === 'IN_PREPARATION' &&
                          isOrigin &&
                          Permissions.canDispatchTransfer(role) && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenDispatch(transfer)}
                              className="h-7 px-2 text-xs bg-amber-50 text-amber-700 border-amber-200 hover:bg-amber-100 font-semibold"
                            >
                              <Truck className="h-3.5 w-3.5 mr-1" />
                              {t('transfers.actions.dispatch')}
                            </Button>
                          )}

                        {/* Receive (IN_TRANSIT & Destination) */}
                        {transfer.status === 'IN_TRANSIT' &&
                          isDestination &&
                          Permissions.canReceiveTransfer(role) && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenReceipt(transfer)}
                              className="h-7 px-2 text-xs bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100 font-semibold"
                            >
                              <Inbox className="h-3.5 w-3.5 mr-1" />
                              {t('transfers.actions.receive')}
                            </Button>
                          )}

                        {/* Cancel (REQUESTED or IN_PREPARATION & Manager/Admin) */}
                        {(transfer.status === 'REQUESTED' ||
                          transfer.status === 'IN_PREPARATION') &&
                          (isOrigin || isDestination) &&
                          Permissions.canCancelTransfer(role) && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleOpenCancel(transfer)}
                              className="h-7 px-1.5 text-xs text-rose-500 hover:text-rose-700 hover:bg-rose-50"
                              title={t('transfers.actions.cancel')}
                            >
                              <Ban className="h-3.5 w-3.5" />
                            </Button>
                          )}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
          </TableBody>
        </Table>

        {/* Pagination Footer */}
        {transfersQuery.data && transfersQuery.data.totalElements > 0 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-slate-200 bg-slate-50/50">
            <span className="text-xs text-slate-500">
              {t('common.pageOf', {
                page: String(page + 1),
                totalPages: String(Math.max(totalPages, 1)),
              })}
            </span>

            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
                disabled={page === 0 || transfersQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                <ChevronLeft className="h-3.5 w-3.5 mr-1" />
                {t('common.previous', { defaultValue: 'Anterior' })}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
                disabled={page >= totalPages - 1 || transfersQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                {t('common.next', { defaultValue: 'Siguiente' })}
                <ChevronRight className="h-3.5 w-3.5 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Dialog Modals */}
      <TransferDetailDialog
        externalId={selectedDetailId}
        open={isDetailOpen}
        onOpenChange={setIsDetailOpen}
      />

      <TransferApprovalDialog
        transfer={activeDetailQuery.data ?? null}
        open={isApprovalOpen}
        onOpenChange={setIsApprovalOpen}
      />

      <TransferDispatchDialog
        transfer={activeDetailQuery.data ?? null}
        open={isDispatchOpen}
        onOpenChange={setIsDispatchOpen}
      />

      <TransferReceiptDialog
        transfer={activeDetailQuery.data ?? null}
        open={isReceiptOpen}
        onOpenChange={setIsReceiptOpen}
      />

      <TransferCancelDialog
        transfer={activeTransferForAction}
        open={isCancelOpen}
        onOpenChange={setIsCancelOpen}
      />
    </div>
  )
}
