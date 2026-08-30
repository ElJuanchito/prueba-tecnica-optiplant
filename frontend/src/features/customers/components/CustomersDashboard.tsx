import * as React from 'react'
import { Plus, RefreshCw, Search, Users } from 'lucide-react'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Input } from '@/components/ui/input.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import {
  useCustomers,
  useDisableCustomer,
  useEnableCustomer,
} from '../hooks/use-customers.ts'
import type {
  CustomerQueryParams,
  CustomerResponse,
} from '../types/index.ts'
import { CustomerTable } from './CustomerTable.tsx'
import { CustomerFormDialog } from './CustomerFormDialog.tsx'
import { CustomerDetailDialog } from './CustomerDetailDialog.tsx'

export function CustomersDashboard() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role

  const canManage = Permissions.canManageCustomers(role)
  const canDeactivate = Permissions.canDeactivateCustomers(role)

  // Filters and pagination state
  const [searchTerm, setSearchTerm] = React.useState<string>('')
  const [activeFilter, setActiveFilter] = React.useState<string>('ALL')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 15

  const queryParams = React.useMemo<CustomerQueryParams>(() => {
    const params: CustomerQueryParams = {
      page,
      size: pageSize,
    }
    if (searchTerm.trim()) {
      params.search = searchTerm.trim()
    }
    if (activeFilter === 'ACTIVE') {
      params.active = true
    } else if (activeFilter === 'INACTIVE') {
      params.active = false
    }
    return params
  }, [searchTerm, activeFilter, page])

  const customersQuery = useCustomers(queryParams)
  const customersPage = customersQuery.data
  const customers = customersPage?.content ?? []
  const totalElements = customersPage?.totalElements ?? 0

  const disableMutation = useDisableCustomer()
  const enableMutation = useEnableCustomer()
  const isTogglingStatus =
    disableMutation.isPending || enableMutation.isPending

  // Dialog states
  const [isFormOpen, setIsFormOpen] = React.useState(false)
  const [editingCustomer, setEditingCustomer] =
    React.useState<CustomerResponse | null>(null)
  const [selectedDetailId, setSelectedDetailId] = React.useState<string | null>(
    null,
  )

  const handleOpenCreate = () => {
    setEditingCustomer(null)
    setIsFormOpen(true)
  }

  const handleOpenEdit = (customer: CustomerResponse) => {
    setEditingCustomer(customer)
    setIsFormOpen(true)
  }

  const handleOpenDetail = (customer: CustomerResponse) => {
    setSelectedDetailId(customer.externalId)
  }

  const handleToggleStatus = (customer: CustomerResponse) => {
    if (customer.active) {
      if (window.confirm(t('customers.actions.confirmDisable'))) {
        disableMutation.mutate(customer.externalId, {
          onSuccess: () => {
            customersQuery.refetch()
          },
        })
      }
    } else {
      if (window.confirm(t('customers.actions.confirmEnable'))) {
        enableMutation.mutate(customer.externalId, {
          onSuccess: () => {
            customersQuery.refetch()
          },
        })
      }
    }
  }

  const handleRefresh = () => {
    customersQuery.refetch()
  }

  return (
    <AppLayout activeModule="customers">
      <div className="space-y-6">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <div className="h-10 w-10 rounded-xl bg-sky-500 text-white flex items-center justify-center shadow-xs">
                <Users className="h-5 w-5" />
              </div>
              <div>
                <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                  {t('customers.title')}
                </h1>
                <p className="text-xs text-slate-500">
                  {t('customers.subtitle')}
                </p>
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2.5">
            {canManage && (
              <Button
                type="button"
                size="sm"
                className="text-xs bg-sky-600 hover:bg-sky-700 text-white shadow-xs font-semibold"
                onClick={handleOpenCreate}
              >
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                {t('customers.newCustomer')}
              </Button>
            )}
          </div>
        </div>

        {/* Filter and Search Bar */}
        <div className="p-3 bg-white border border-slate-200 rounded-xl shadow-2xs flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5 flex-1 min-w-[280px]">
            {/* Search Input */}
            <div className="relative flex-1 min-w-[200px] max-w-md">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
              <Input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value)
                  setPage(0)
                }}
                placeholder={t('customers.searchPlaceholder')}
                className="pl-8 text-xs h-8 bg-slate-50 border-slate-200"
              />
            </div>

            {/* Status Select */}
            <div className="w-40">
              <select
                value={activeFilter}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                  setActiveFilter(e.target.value)
                  setPage(0)
                }}
                className="w-full flex h-8 rounded-md border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-sky-500"
              >
                <option value="ALL">{t('common.allStatus')}</option>
                <option value="ACTIVE">{t('common.activeOnly')}</option>
                <option value="INACTIVE">{t('common.disabledOnly')}</option>
              </select>
            </div>
          </div>

          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-8 text-xs border-slate-200 text-slate-700 hover:bg-slate-50"
            onClick={handleRefresh}
            title={t('common.refresh')}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 mr-1 text-slate-500 ${
                customersQuery.isFetching ? 'animate-spin' : ''
              }`}
            />
            {t('common.refresh')}
          </Button>
        </div>

        {/* Customer Table */}
        <CustomerTable
          customers={customers}
          isLoading={customersQuery.isLoading}
          totalElements={totalElements}
          page={page}
          size={pageSize}
          onPageChange={setPage}
          onViewDetail={handleOpenDetail}
          onEdit={handleOpenEdit}
          onToggleStatus={handleToggleStatus}
          canManage={canManage}
          canDeactivate={canDeactivate}
          isTogglingStatus={isTogglingStatus}
        />

        {/* Create / Edit Dialog */}
        <CustomerFormDialog
          open={isFormOpen}
          onOpenChange={setIsFormOpen}
          customerToEdit={editingCustomer}
          onSuccess={() => {
            customersQuery.refetch()
          }}
        />

        {/* Detail and Sales History Dialog */}
        <CustomerDetailDialog
          customerExternalId={selectedDetailId}
          open={Boolean(selectedDetailId)}
          onOpenChange={(open) => {
            if (!open) setSelectedDetailId(null)
          }}
        />
      </div>
    </AppLayout>
  )
}
