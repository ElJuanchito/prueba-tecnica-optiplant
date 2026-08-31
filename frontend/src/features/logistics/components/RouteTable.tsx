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
import { useDeactivateRoute, useRoutes } from '../hooks/use-logistics.ts'
import { RouteFormDialog } from './RouteFormDialog.tsx'
import { ROUTE_SORT } from '../schemas/route.schema.ts'
import type {
  RouteQueryParams,
  RouteResponse,
  RouteSortOption,
} from '../types/route.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  DollarSign,
  Edit2,
  Plus,
  PowerOff,
  RefreshCw,
  Route as RouteIcon,
} from 'lucide-react'

// Each RouteSortOption fixes both a field and a direction (RF-LOG-03) — there is
// no ASC/DESC toggle, so clicking a header selects that fixed criterion rather
// than flipping direction, unlike CorporateBoardView's free field/direction sort.
function renderSortIndicator(
  criterion: RouteSortOption,
  activeSort: RouteSortOption,
) {
  if (activeSort !== criterion) {
    return (
      <ArrowUpDown className="h-3 w-3 ml-1 text-slate-300 group-hover:text-slate-500 inline" />
    )
  }
  return criterion === ROUTE_SORT.PRIORITY_DESC ? (
    <ArrowDown className="h-3.5 w-3.5 ml-1 text-indigo-600 inline" />
  ) : (
    <ArrowUp className="h-3.5 w-3.5 ml-1 text-indigo-600 inline" />
  )
}

export function RouteTable() {
  const { t } = useTranslation()
  const [activeFilter, setActiveFilter] = React.useState<string>('ALL')
  // Mirrors the backend's default (LogisticsController#listRoutes): the most
  // urgent routes surface first when no explicit sort has been chosen yet.
  const [sort, setSort] = React.useState<RouteSortOption>(
    ROUTE_SORT.PRIORITY_DESC,
  )
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 10

  const queryParams: RouteQueryParams = {
    page,
    size: pageSize,
    sort,
    active:
      activeFilter === 'ACTIVE'
        ? true
        : activeFilter === 'INACTIVE'
          ? false
          : undefined,
  }

  const routesQuery = useRoutes(queryParams)
  const deactivateMutation = useDeactivateRoute()

  const handleSort = (criterion: RouteSortOption) => {
    setSort(criterion)
    setPage(0)
  }

  const [routeToEdit, setRouteToEdit] = React.useState<RouteResponse | null>(
    null,
  )
  const [isFormOpen, setIsFormOpen] = React.useState(false)

  const handleCreate = () => {
    setRouteToEdit(null)
    setIsFormOpen(true)
  }

  const handleEdit = (route: RouteResponse) => {
    setRouteToEdit(route)
    setIsFormOpen(true)
  }

  const handleDeactivate = (route: RouteResponse) => {
    if (
      window.confirm(
        `Are you sure you want to deactivate route ${route.originBranch?.name} → ${route.destinationBranch?.name}?`,
      )
    ) {
      deactivateMutation.mutate(route.externalId)
    }
  }

  const totalPages = routesQuery.data
    ? Math.ceil(routesQuery.data.totalElements / pageSize)
    : 1

  return (
    <div className="space-y-4">
      {/* Top Filter and Actions Bar */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs">
        <div className="flex items-center gap-2.5">
          <div className="w-40">
            <Select
              value={activeFilter}
              onValueChange={(val) => {
                setActiveFilter(val)
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-9 bg-slate-50 border-slate-200">
                <SelectValue placeholder="Estado" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL" className="text-xs">
                  {t('common.all')}
                </SelectItem>
                <SelectItem value="ACTIVE" className="text-xs">
                  {t('logistics.routes.active')}
                </SelectItem>
                <SelectItem value="INACTIVE" className="text-xs">
                  {t('logistics.routes.inactive')}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={() => routesQuery.refetch()}
            disabled={routesQuery.isFetching}
            className="text-xs h-9 gap-1 text-slate-600"
            title={t('common.refresh')}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 ${
                routesQuery.isFetching ? 'animate-spin' : ''
              }`}
            />
            <span className="hidden md:inline">{t('common.refresh')}</span>
          </Button>
        </div>

        <Button
          onClick={handleCreate}
          size="sm"
          className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white font-semibold gap-1.5 h-9"
        >
          <Plus className="h-4 w-4" />
          {t('logistics.routes.createRoute')}
        </Button>
      </div>

      {/* Routes Table Container */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-2xs overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50/80 border-b border-slate-200">
            <TableRow>
              <TableHead className="text-xs font-bold text-slate-700 py-3">
                {t('logistics.routes.origin')} →{' '}
                {t('logistics.routes.destination')}
              </TableHead>
              <TableHead
                onClick={() => handleSort(ROUTE_SORT.DURATION_ASC)}
                className="text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
              >
                {t('logistics.routes.durationHours')}
                {renderSortIndicator(ROUTE_SORT.DURATION_ASC, sort)}
              </TableHead>
              <TableHead
                onClick={() => handleSort(ROUTE_SORT.COST_ASC)}
                className="text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
              >
                {t('logistics.routes.cost')}
                {renderSortIndicator(ROUTE_SORT.COST_ASC, sort)}
              </TableHead>
              <TableHead
                onClick={() => handleSort(ROUTE_SORT.PRIORITY_DESC)}
                className="text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
              >
                {t('logistics.routes.priority')}
                {renderSortIndicator(ROUTE_SORT.PRIORITY_DESC, sort)}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('logistics.routes.status')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right pr-4">
                {t('common.actions')}
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {routesQuery.isLoading && (
              <>
                {[...Array(4)].map((_, i) => (
                  <TableRow key={i}>
                    <TableCell>
                      <Skeleton className="h-4 w-48" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-20" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-20" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-5 w-16" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-5 w-16" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-7 w-20 ml-auto" />
                    </TableCell>
                  </TableRow>
                ))}
              </>
            )}

            {!routesQuery.isLoading &&
              routesQuery.data?.content.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={6}
                    className="text-center py-12 text-slate-400"
                  >
                    <RouteIcon className="h-8 w-8 mx-auto mb-2 text-slate-300" />
                    <p className="text-sm font-semibold">
                      {t('common.noData')}
                    </p>
                  </TableCell>
                </TableRow>
              )}

            {!routesQuery.isLoading &&
              routesQuery.data?.content.map((route) => (
                <TableRow
                  key={route.externalId}
                  className="hover:bg-slate-50/60"
                >
                  {/* Origin -> Destination */}
                  <TableCell className="text-xs">
                    <div className="flex items-center gap-1.5 text-slate-800 font-semibold">
                      <span>{route.originBranch?.name ?? '—'}</span>
                      <ArrowRight className="h-3 w-3 text-slate-400 shrink-0" />
                      <span>{route.destinationBranch?.name ?? '—'}</span>
                    </div>
                  </TableCell>

                  {/* Duration */}
                  <TableCell className="text-xs text-slate-700 font-mono">
                    <div className="flex items-center gap-1">
                      <Clock className="h-3 w-3 text-slate-400" />
                      {route.estimatedDurationHours} hrs
                    </div>
                  </TableCell>

                  {/* Cost */}
                  <TableCell className="text-xs text-slate-700 font-mono">
                    <div className="flex items-center gap-0.5">
                      <DollarSign className="h-3 w-3 text-slate-400" />
                      {Number(route.transportCost).toFixed(2)}
                    </div>
                  </TableCell>

                  {/* Priority */}
                  <TableCell>
                    <Badge variant="outline" className="text-[10px]">
                      {route.priorityLevel}
                    </Badge>
                  </TableCell>

                  {/* Status */}
                  <TableCell>
                    {route.active ? (
                      <Badge
                        variant="outline"
                        className="bg-emerald-50 text-emerald-700 border-emerald-200 text-[10px]"
                      >
                        {t('logistics.routes.active')}
                      </Badge>
                    ) : (
                      <Badge
                        variant="outline"
                        className="bg-slate-100 text-slate-500 border-slate-200 text-[10px]"
                      >
                        {t('logistics.routes.inactive')}
                      </Badge>
                    )}
                  </TableCell>

                  {/* Actions */}
                  <TableCell className="text-right pr-4">
                    <div className="flex items-center justify-end gap-1.5">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleEdit(route)}
                        className="h-7 px-2 text-xs text-slate-600 hover:text-slate-900"
                        title={t('common.edit')}
                      >
                        <Edit2 className="h-3.5 w-3.5 mr-1" />
                        <span className="hidden sm:inline">
                          {t('common.edit')}
                        </span>
                      </Button>

                      {route.active && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDeactivate(route)}
                          disabled={deactivateMutation.isPending}
                          className="h-7 px-1.5 text-xs text-rose-500 hover:text-rose-700 hover:bg-rose-50"
                          title={t('logistics.routes.deactivateRoute')}
                        >
                          <PowerOff className="h-3.5 w-3.5" />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
          </TableBody>
        </Table>

        {/* Pagination Footer */}
        {routesQuery.data && routesQuery.data.totalElements > 0 && (
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
                disabled={page === 0 || routesQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                <ChevronLeft className="h-3.5 w-3.5 mr-1" />
                {t('common.previous', { defaultValue: 'Anterior' })}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
                disabled={page >= totalPages - 1 || routesQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                {t('common.next', { defaultValue: 'Siguiente' })}
                <ChevronRight className="h-3.5 w-3.5 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Form Dialog */}
      <RouteFormDialog
        routeToEdit={routeToEdit}
        open={isFormOpen}
        onOpenChange={setIsFormOpen}
      />
    </div>
  )
}
