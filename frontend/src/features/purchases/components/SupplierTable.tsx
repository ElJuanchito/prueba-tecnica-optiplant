import { Edit2, EyeOff, Power } from 'lucide-react'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import type { SupplierResponse } from '../types/index.ts'

interface SupplierTableProps {
  suppliers: SupplierResponse[]
  isLoading: boolean
  totalElements: number
  page: number
  size: number
  onPageChange: (page: number) => void
  onEdit: (supplier: SupplierResponse) => void
  onToggleStatus: (supplier: SupplierResponse) => void
  isAdmin: boolean
}

export function SupplierTable({
  suppliers,
  isLoading,
  totalElements,
  page,
  size,
  onPageChange,
  onEdit,
  onToggleStatus,
  isAdmin,
}: SupplierTableProps) {
  const { t } = useTranslation()
  const totalPages = Math.ceil(totalElements / size) || 1

  if (isLoading) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-2xs">
        <div className="inline-flex h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-rose-600" />
        <p className="mt-2 text-xs text-slate-500">{t('common.loading')}</p>
      </div>
    )
  }

  if (suppliers.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-2xs">
        <p className="text-sm font-semibold text-slate-700">
          {t('purchases.suppliers.noSuppliers')}
        </p>
        {!isAdmin && (
          <p className="mt-1 text-xs text-slate-400">
            {t('purchases.suppliers.adminOnlyNotice')}
          </p>
        )}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="bg-slate-50/75 hover:bg-slate-50/75">
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.suppliers.name')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.suppliers.taxId')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.suppliers.contactName')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.suppliers.phone')} /{' '}
                {t('purchases.suppliers.email')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.suppliers.address')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('common.status')}
              </TableHead>
              {isAdmin && (
                <TableHead className="text-right text-xs font-bold text-slate-700">
                  {t('common.actions')}
                </TableHead>
              )}
            </TableRow>
          </TableHeader>
          <TableBody>
            {suppliers.map((s) => (
              <TableRow key={s.externalId} className="hover:bg-slate-50/50">
                <TableCell className="font-semibold text-slate-900 text-xs">
                  {s.name}
                </TableCell>
                <TableCell className="font-mono text-xs text-slate-600">
                  {s.taxId}
                </TableCell>
                <TableCell className="text-xs text-slate-700">
                  {s.contactName || (
                    <span className="text-slate-400 italic">—</span>
                  )}
                </TableCell>
                <TableCell className="text-xs text-slate-600">
                  <div className="space-y-0.5">
                    {s.phone && <div>{s.phone}</div>}
                    {s.email && (
                      <div className="text-[11px] text-slate-400">
                        {s.email}
                      </div>
                    )}
                    {!s.phone && !s.email && (
                      <span className="text-slate-400 italic">—</span>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-xs text-slate-600 max-w-[200px] truncate">
                  {s.address || (
                    <span className="text-slate-400 italic">—</span>
                  )}
                </TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={`text-[10px] font-semibold ${
                      s.active
                        ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                        : 'border-slate-200 bg-slate-100 text-slate-500'
                    }`}
                  >
                    {s.active ? t('common.active') : t('common.disabled')}
                  </Badge>
                </TableCell>
                {isAdmin && (
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-7 w-7 p-0 text-slate-500 hover:text-slate-900"
                        onClick={() => onEdit(s)}
                        title={t('common.edit')}
                      >
                        <Edit2 className="h-3.5 w-3.5" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className={`h-7 w-7 p-0 ${
                          s.active
                            ? 'text-slate-400 hover:text-rose-600 hover:bg-rose-50'
                            : 'text-slate-400 hover:text-emerald-600 hover:bg-emerald-50'
                        }`}
                        onClick={() => onToggleStatus(s)}
                        title={
                          s.active
                            ? t('purchases.suppliers.disable')
                            : t('purchases.suppliers.enable')
                        }
                      >
                        {s.active ? (
                          <EyeOff className="h-3.5 w-3.5" />
                        ) : (
                          <Power className="h-3.5 w-3.5" />
                        )}
                      </Button>
                    </div>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between text-xs text-slate-500 px-1">
        <span>
          {t('common.showing')}{' '}
          <strong className="font-semibold text-slate-700">
            {totalElements > 0 ? page * size + 1 : 0}
          </strong>{' '}
          {t('common.to')}{' '}
          <strong className="font-semibold text-slate-700">
            {Math.min((page + 1) * size, totalElements)}
          </strong>{' '}
          {t('common.of')}{' '}
          <strong className="font-semibold text-slate-700">
            {totalElements}
          </strong>{' '}
          {t('common.results')}
        </span>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
            className="h-7 text-xs px-2.5"
          >
            {t('common.previous')}
          </Button>
          <span className="font-medium">
            {page + 1} / {totalPages}
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => onPageChange(page + 1)}
            className="h-7 text-xs px-2.5"
          >
            {t('common.next')}
          </Button>
        </div>
      </div>
    </div>
  )
}
