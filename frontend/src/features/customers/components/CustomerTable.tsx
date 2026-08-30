import * as React from 'react'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Edit2,
  Eye,
  Mail,
  MapPin,
  Phone,
  PowerOff,
  User,
  Users,
} from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import type { CustomerResponse } from '../types/index.ts'

interface CustomerTableProps {
  customers: CustomerResponse[]
  isLoading?: boolean
  totalElements: number
  page: number
  size: number
  onPageChange: (newPage: number) => void
  onViewDetail: (customer: CustomerResponse) => void
  onEdit: (customer: CustomerResponse) => void
  onToggleStatus: (customer: CustomerResponse) => void
  canManage?: boolean
  canDeactivate?: boolean
  isTogglingStatus?: boolean
}

export function CustomerTable({
  customers,
  isLoading = false,
  totalElements,
  page,
  size,
  onPageChange,
  onViewDetail,
  onEdit,
  onToggleStatus,
  canManage = false,
  canDeactivate = false,
  isTogglingStatus = false,
}: CustomerTableProps) {
  const { t } = useTranslation()
  const totalPages = Math.ceil(totalElements / size) || 1

  const columns = React.useMemo<ColumnDef<CustomerResponse>[]>(
    () => [
      {
        accessorKey: 'name',
        header: t('customers.table.name'),
        cell: ({ row }) => {
          const customer = row.original
          const initial = customer.name.charAt(0).toUpperCase()
          return (
            <div className="flex items-center gap-2.5">
              <div className="h-8 w-8 rounded-lg bg-sky-50 text-sky-700 font-bold text-xs flex items-center justify-center border border-sky-200 shrink-0">
                {initial || <User className="h-4 w-4" />}
              </div>
              <div className="min-w-0">
                <div className="font-semibold text-xs text-slate-900 truncate">
                  {customer.name}
                </div>
                {customer.taxId && (
                  <div className="font-mono text-[11px] text-slate-500">
                    NIT: {customer.taxId}
                  </div>
                )}
              </div>
            </div>
          )
        },
      },
      {
        accessorKey: 'contact',
        header: t('customers.table.contact'),
        cell: ({ row }) => {
          const customer = row.original
          const hasContact = customer.email || customer.phone || customer.address
          if (!hasContact) {
            return <span className="text-xs text-slate-400">—</span>
          }
          return (
            <div className="space-y-0.5 text-xs text-slate-600">
              {customer.email && (
                <div className="flex items-center gap-1.5 truncate max-w-xs">
                  <Mail className="h-3 w-3 text-slate-400 shrink-0" />
                  <span className="truncate">{customer.email}</span>
                </div>
              )}
              {customer.phone && (
                <div className="flex items-center gap-1.5">
                  <Phone className="h-3 w-3 text-slate-400 shrink-0" />
                  <span>{customer.phone}</span>
                </div>
              )}
              {customer.address && (
                <div className="flex items-center gap-1.5 truncate max-w-xs text-slate-500 text-[11px]">
                  <MapPin className="h-3 w-3 text-slate-400 shrink-0" />
                  <span className="truncate">{customer.address}</span>
                </div>
              )}
            </div>
          )
        },
      },
      {
        accessorKey: 'active',
        header: t('customers.table.status'),
        cell: ({ row }) => {
          const active = row.original.active
          return (
            <Badge
              variant="outline"
              className={`text-[10px] font-bold py-0.5 px-2 ${
                active
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                  : 'bg-slate-100 text-slate-600 border-slate-300'
              }`}
            >
              {active ? t('common.active') : t('common.inactive')}
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: ({ row }) => {
          const customer = row.original
          return (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs text-slate-700 hover:text-sky-700 border-slate-300"
                onClick={() => onViewDetail(customer)}
                title={t('common.details')}
              >
                <Eye className="h-3.5 w-3.5 mr-1 text-sky-600" />
                {t('common.details')}
              </Button>

              {canManage && (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 px-2 text-xs text-slate-700 hover:text-slate-900 border-slate-300"
                  onClick={() => onEdit(customer)}
                  title={t('common.edit')}
                >
                  <Edit2 className="h-3.5 w-3.5 mr-1 text-slate-500" />
                  {t('common.edit')}
                </Button>
              )}

              {canDeactivate && (
                <Button
                  variant="outline"
                  size="sm"
                  className={`h-7 px-2 text-xs ${
                    customer.active
                      ? 'text-rose-700 hover:text-rose-800 hover:bg-rose-50 border-rose-200'
                      : 'text-emerald-700 hover:text-emerald-800 hover:bg-emerald-50 border-emerald-200'
                  }`}
                  onClick={() => onToggleStatus(customer)}
                  disabled={isTogglingStatus}
                  title={
                    customer.active
                      ? t('customers.actions.disable')
                      : t('customers.actions.enable')
                  }
                >
                  {customer.active ? (
                    <>
                      <PowerOff className="h-3.5 w-3.5 mr-1 text-rose-600" />
                      {t('common.disable')}
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="h-3.5 w-3.5 mr-1 text-emerald-600" />
                      {t('common.enable')}
                    </>
                  )}
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [t, onViewDetail, onEdit, onToggleStatus, canManage, canDeactivate, isTogglingStatus],
  )

  const table = useReactTable({
    data: customers,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  if (isLoading) {
    return (
      <div className="space-y-3 p-4 bg-white rounded-xl border border-slate-200 shadow-2xs">
        <Skeleton className="h-10 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
      </div>
    )
  }

  return (
    <div className="space-y-3">
      <div className="rounded-xl border border-slate-200 bg-white overflow-hidden shadow-2xs">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-200">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    className="text-xs font-bold text-slate-700 uppercase tracking-wider h-10 px-3"
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-32 text-center text-slate-400 text-xs"
                >
                  <div className="flex flex-col items-center justify-center gap-1.5">
                    <Users className="h-6 w-6 text-slate-300" />
                    <span>{t('customers.table.noCustomers')}</span>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="hover:bg-slate-50/80 transition-colors border-b border-slate-100 last:border-0"
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="py-2.5 px-3">
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination Footer */}
      {totalElements > 0 && (
        <div className="flex items-center justify-between px-2 py-1 text-xs text-slate-600">
          <div>
            {t('common.showing')}{' '}
            <span className="font-semibold text-slate-900">
              {customers.length === 0 ? 0 : page * size + 1}
            </span>{' '}
            {t('common.to')}{' '}
            <span className="font-semibold text-slate-900">
              {Math.min((page + 1) * size, totalElements)}
            </span>{' '}
            {t('common.of')}{' '}
            <span className="font-semibold text-slate-900">
              {totalElements}
            </span>{' '}
            {t('common.results')}
          </div>

          <div className="flex items-center gap-2">
            <span className="text-slate-500 font-medium">
              {t('common.pageOf', {
                page: String(page + 1),
                totalPages: String(totalPages),
              })}
            </span>
            <Button
              variant="outline"
              size="sm"
              className="h-7 w-7 p-0 border-slate-300"
              disabled={page <= 0}
              onClick={() => onPageChange(page - 1)}
              title={t('common.previous')}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="h-7 w-7 p-0 border-slate-300"
              disabled={page + 1 >= totalPages}
              onClick={() => onPageChange(page + 1)}
              title={t('common.next')}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
