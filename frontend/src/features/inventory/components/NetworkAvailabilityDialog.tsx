import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useNetworkAvailability } from '../hooks/use-inventory.ts'
import { AlertCircle, Building2, Globe } from 'lucide-react'

export interface NetworkAvailabilityProduct {
  productExternalId?: string
  externalId?: string
  name: string
  sku: string
}

interface NetworkAvailabilityDialogProps {
  product: NetworkAvailabilityProduct | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function NetworkAvailabilityDialog({
  product,
  open,
  onOpenChange,
}: NetworkAvailabilityDialogProps) {
  const productExternalId = product?.productExternalId ?? product?.externalId ?? ''
  const networkQuery = useNetworkAvailability(productExternalId, open)

  const data = networkQuery.data

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl bg-white p-6 sm:rounded-xl">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center border border-indigo-200">
              <Globe className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                Network Stock Availability
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                Real-time stock balances across all active branches in the network
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="py-2 space-y-4">
          {product && (
            <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-900">
                  {product.name}
                </p>
                <p className="text-[11px] text-slate-500 font-mono">
                  SKU: {product.sku}
                </p>
              </div>
              <div className="text-right">
                <span className="text-xs text-slate-500">Network Total: </span>
                <span className="text-sm font-bold text-indigo-700">
                  {data ? data.networkTotal : '...'} units
                </span>
              </div>
            </div>
          )}

          {networkQuery.isLoading && (
            <div className="space-y-2 py-4">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          )}

          {networkQuery.isError && (
            <div className="p-4 bg-rose-50 border border-rose-200 rounded-lg flex items-center gap-3 text-rose-700 text-xs">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>
                {networkQuery.error instanceof Error
                  ? networkQuery.error.message
                  : 'Failed to load network stock availability'}
              </span>
            </div>
          )}

          {data && (
            <div className="border border-slate-200 rounded-lg overflow-hidden">
              <Table>
                <TableHeader className="bg-slate-50">
                  <TableRow>
                    <TableHead className="text-xs font-semibold text-slate-700">
                      Branch
                    </TableHead>
                    <TableHead className="text-xs font-semibold text-slate-700 text-right">
                      Current
                    </TableHead>
                    <TableHead className="text-xs font-semibold text-slate-700 text-right">
                      Reserved
                    </TableHead>
                    <TableHead className="text-xs font-semibold text-slate-700 text-right">
                      In-Transit
                    </TableHead>
                    <TableHead className="text-xs font-semibold text-slate-700 text-right">
                      Available
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.branches.length === 0 ? (
                    <TableRow>
                      <TableCell
                        colSpan={5}
                        className="text-center py-6 text-xs text-slate-500"
                      >
                        No branch records found for this product.
                      </TableCell>
                    </TableRow>
                  ) : (
                    data.branches.map((b) => (
                      <TableRow
                        key={b.branchExternalId}
                        className={
                          b.isOwnBranch
                            ? 'bg-indigo-50/50 hover:bg-indigo-50 font-medium'
                            : 'hover:bg-slate-50'
                        }
                      >
                        <TableCell className="text-xs py-2.5">
                          <div className="flex items-center gap-2">
                            <Building2 className="h-3.5 w-3.5 text-slate-400" />
                            <span className="font-semibold text-slate-900">
                              {b.branchName}
                            </span>
                            {b.isOwnBranch && (
                              <Badge
                                variant="default"
                                className="text-[10px] py-0 px-1.5 bg-indigo-600"
                              >
                                Own Branch
                              </Badge>
                            )}
                          </div>
                        </TableCell>
                        <TableCell className="text-xs text-right py-2.5 text-slate-700 font-mono">
                          {b.currentStock}
                        </TableCell>
                        <TableCell className="text-xs text-right py-2.5 text-amber-600 font-mono">
                          {b.reservedStock}
                        </TableCell>
                        <TableCell className="text-xs text-right py-2.5 text-sky-600 font-mono">
                          {b.inTransitStock}
                        </TableCell>
                        <TableCell className="text-xs text-right py-2.5 font-bold font-mono text-emerald-700">
                          {b.availableStock}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="text-xs"
          >
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
