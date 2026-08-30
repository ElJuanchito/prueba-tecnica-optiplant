import * as React from 'react'
import { AlertCircle, Search } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { saleService } from '../services/sale.service.ts'
import type { SaleDetailResponse } from '../types/index.ts'

interface InvoiceSearchDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onFoundSale: (sale: SaleDetailResponse) => void
}

export function InvoiceSearchDialog({
  open,
  onOpenChange,
  onFoundSale,
}: InvoiceSearchDialogProps) {
  const { t } = useTranslation()
  const [invoiceNumber, setInvoiceNumber] = React.useState('')
  const [isSearching, setIsSearching] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)

  React.useEffect(() => {
    if (open) {
      setInvoiceNumber('')
      setError(null)
      setIsSearching(false)
    }
  }, [open])

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = invoiceNumber.trim()
    if (!trimmed) return

    setIsSearching(true)
    setError(null)

    try {
      const result = await saleService.getByInvoiceNumber(trimmed)
      onOpenChange(false)
      onFoundSale(result)
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : t('sales.searchNotFound')
      setError(msg)
    } finally {
      setIsSearching(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <form onSubmit={handleSearch} className="space-y-4">
          <DialogHeader>
            <div className="flex items-center gap-2 text-teal-600">
              <div className="h-8 w-8 rounded-lg bg-teal-50 border border-teal-200 flex items-center justify-center">
                <Search className="h-4 w-4" />
              </div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('sales.searchByInvoice')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500 pt-1">
              {t('sales.searchByInvoicePrompt')}
            </DialogDescription>
          </DialogHeader>

          {error && (
            <Alert variant="destructive" className="py-2 px-3 text-xs">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle className="text-xs font-semibold">
                {t('common.error')}
              </AlertTitle>
              <AlertDescription className="text-[11px]">{error}</AlertDescription>
            </Alert>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="invoice-search-input" className="text-xs font-semibold">
              {t('sales.table.invoiceNumber')}
            </Label>
            <Input
              id="invoice-search-input"
              value={invoiceNumber}
              onChange={(e) => setInvoiceNumber(e.target.value)}
              placeholder="VEN-2026-0001"
              className="text-xs h-9 font-mono"
              autoFocus
            />
          </div>

          <DialogFooter className="gap-2 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs"
              onClick={() => onOpenChange(false)}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="default"
              size="sm"
              className="text-xs bg-teal-600 hover:bg-teal-700 text-white"
              disabled={isSearching || !invoiceNumber.trim()}
            >
              {isSearching ? (
                <span>{t('common.search')}...</span>
              ) : (
                <span className="flex items-center gap-1.5">
                  <Search className="h-3.5 w-3.5" />
                  {t('sales.lookup')}
                </span>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
