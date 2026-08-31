import type { PurchaseOrderDetailResponse } from '../types/index.ts'

interface PurchaseOrderPrintDocumentProps {
  order: PurchaseOrderDetailResponse
}

export function PurchaseOrderPrintDocument({
  order,
}: PurchaseOrderPrintDocumentProps) {
  const formatDate = (isoString?: string | null) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleDateString('es-ES', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    } catch {
      return isoString
    }
  }

  const formatDateTime = (isoString?: string | null) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleString('es-ES', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  // Calculate gross subtotal and total discounts
  const grossSubtotal = order.items.reduce(
    (acc, item) => acc + item.orderedQuantity * item.unitCost,
    0,
  )
  const totalDiscount = grossSubtotal - order.totalAmount

  return (
    <div
      id="printable-purchase-order"
      className="hidden print:block bg-white text-slate-900 font-sans p-6 text-xs leading-normal max-w-4xl mx-auto"
    >
      {/* Top Header */}
      <div className="flex items-start justify-between border-b-2 border-slate-900 pb-4 mb-5">
        <div>
          <div className="text-2xl font-black tracking-tight text-slate-900">
            <span className="text-emerald-600">Opti</span>Plant
          </div>
          <div className="text-xs font-semibold text-slate-600 tracking-wide uppercase mt-0.5">
            Sistema de Gestión Agroindustrial
          </div>
          <div className="text-[11px] text-slate-500 mt-1">
            RUC / NIT: 1792345678001 • División de Abastecimiento y Compras
          </div>
        </div>

        <div className="text-right">
          <div className="inline-block bg-slate-900 text-white font-bold text-xs uppercase px-3 py-1 rounded tracking-wider mb-1">
            Orden de Compra
          </div>
          <div className="text-xl font-mono font-black text-rose-700">
            {order.orderNumber}
          </div>
          <div className="text-[11px] text-slate-600 mt-1">
            <strong>Fecha de Emisión:</strong> {formatDate(order.createdAt)}
          </div>
          <div className="text-[11px] text-slate-600">
            <strong>Estado:</strong>{' '}
            <span className="font-semibold uppercase text-slate-800">
              {order.status}
            </span>
          </div>
        </div>
      </div>

      {/* Parties Info Grid */}
      <div className="grid grid-cols-2 gap-4 mb-5">
        {/* Supplier Box */}
        <div className="border border-slate-200 rounded-lg p-3.5 bg-slate-50/50 space-y-1 print-avoid-break">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500 border-b border-slate-200 pb-1 mb-1.5">
            Datos del Proveedor
          </div>
          <div className="text-sm font-bold text-slate-900">
            {order.supplier.name}
          </div>
          {order.supplier.taxId && (
            <div className="text-xs text-slate-600">
              <span className="font-semibold">NIT/RUC:</span> {order.supplier.taxId}
            </div>
          )}
          <div className="text-xs text-slate-600">
            <span className="font-semibold">Condición comercial:</span>{' '}
            {order.paymentTerms ? `Plazo acordado: ${order.paymentTerms}` : 'Contado / Pago inmediato'}
          </div>
        </div>

        {/* Destination & Branch Box */}
        <div className="border border-slate-200 rounded-lg p-3.5 bg-slate-50/50 space-y-1 print-avoid-break">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500 border-b border-slate-200 pb-1 mb-1.5">
            Consignación y Entrega
          </div>
          <div className="text-sm font-bold text-slate-900">
            {order.branch?.name || 'Sucursal Matriz'}
          </div>
          <div className="text-xs text-slate-600">
            <span className="font-semibold">Emitido por:</span>{' '}
            {order.createdBy?.username || 'Administración'}
          </div>
          <div className="text-xs text-slate-600">
            <span className="font-semibold">Moneda:</span> Dólares Americanos ($ USD)
          </div>
        </div>
      </div>

      {/* Notes / Special Instructions */}
      {order.notes && (
        <div className="border border-amber-200 bg-amber-50/50 rounded-lg p-3 mb-5 text-xs text-slate-800 print-avoid-break">
          <span className="font-bold text-amber-900 block mb-0.5">
            Instrucciones de Despacho y Entrega:
          </span>
          <p className="text-slate-700 font-medium">Instrucción: {order.notes}</p>
        </div>
      )}

      {/* Items Table */}
      <div className="mb-5 print-avoid-break">
        <div className="text-xs font-bold uppercase tracking-wider text-slate-700 mb-2">
          Detalle de Ítems Solicitados
        </div>
        <table className="w-full border-collapse text-left text-xs">
          <thead>
            <tr className="bg-slate-900 text-white font-bold text-[11px] uppercase">
              <th className="py-2 px-2.5 w-8 text-center">#</th>
              <th className="py-2 px-2.5 w-28">SKU</th>
              <th className="py-2 px-2.5">Descripción del Producto</th>
              <th className="py-2 px-2.5 text-right w-16">Cant.</th>
              <th className="py-2 px-2.5 text-right w-24">Costo Unit.</th>
              <th className="py-2 px-2.5 text-right w-16">Desc. %</th>
              <th className="py-2 px-2.5 text-right w-24">Costo Efec.</th>
              <th className="py-2 px-2.5 text-right w-24">Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((item, idx) => (
              <tr
                key={item.externalId}
                className="border-b border-slate-200 even:bg-slate-50/75 text-slate-800"
              >
                <td className="py-2 px-2.5 text-center font-mono text-[11px] text-slate-500">
                  {idx + 1}
                </td>
                <td className="py-2 px-2.5 font-mono font-bold text-rose-700">
                  {item.sku}
                </td>
                <td className="py-2 px-2.5 font-semibold text-slate-900">
                  {item.name}
                </td>
                <td className="py-2 px-2.5 text-right font-mono font-bold">
                  {item.orderedQuantity}
                </td>
                <td className="py-2 px-2.5 text-right font-mono text-slate-600">
                  ${item.unitCost.toFixed(2)}
                </td>
                <td className="py-2 px-2.5 text-right font-mono text-slate-600">
                  {item.discountPercent > 0 ? `${item.discountPercent}%` : '0%'}
                </td>
                <td className="py-2 px-2.5 text-right font-mono font-semibold text-slate-800">
                  ${item.effectiveUnitCost.toFixed(2)}
                </td>
                <td className="py-2 px-2.5 text-right font-mono font-bold text-slate-900">
                  ${item.subtotal.toFixed(2)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Totals Summary */}
      <div className="flex justify-end mb-8 print-avoid-break">
        <div className="w-64 border border-slate-200 rounded-lg p-3 bg-slate-50/50 space-y-1.5 text-xs">
          <div className="flex justify-between text-slate-600">
            <span>Subtotal Bruto:</span>
            <span className="font-mono font-semibold">${grossSubtotal.toFixed(2)}</span>
          </div>
          {totalDiscount > 0 && (
            <div className="flex justify-between text-amber-700 font-medium">
              <span>Descuento Aplicado:</span>
              <span className="font-mono">-${totalDiscount.toFixed(2)}</span>
            </div>
          )}
          <div className="pt-2 border-t-2 border-slate-900 flex justify-between text-sm font-black text-slate-900">
            <span>TOTAL AUTORIZADO:</span>
            <span className="font-mono text-base text-rose-700">
              ${order.totalAmount.toFixed(2)}
            </span>
          </div>
        </div>
      </div>

      {/* Commercial Terms Notice */}
      <div className="border-t border-slate-200 pt-3 mb-8 text-[10px] text-slate-500 text-justify print-avoid-break">
        <p>
          <strong>Términos y Condiciones:</strong> Esta orden de compra constituye una solicitud formal de suministro sujeta a los términos y especificaciones acordadas. La mercadería recibida estará sujeta a inspección física y control de calidad al momento del desembarque en bodega. Cualquier discrepancia en cantidades, embalaje o calidad deberá ser notificada de forma inmediata.
        </p>
      </div>

      {/* Signatures Section */}
      <div className="grid grid-cols-3 gap-8 pt-8 border-t border-dashed border-slate-300 print-avoid-break text-center">
        <div className="space-y-1">
          <div className="border-b border-slate-400 pb-8" />
          <div className="text-xs font-bold text-slate-900 pt-1">
            {order.createdBy?.username || 'Elaborado por'}
          </div>
          <div className="text-[10px] text-slate-500">Solicitante / Compras</div>
        </div>

        <div className="space-y-1">
          <div className="border-b border-slate-400 pb-8" />
          <div className="text-xs font-bold text-slate-900 pt-1">
            Autorizado / Gerencia
          </div>
          <div className="text-[10px] text-slate-500">Firma y Aprobación</div>
        </div>

        <div className="space-y-1">
          <div className="border-b border-slate-400 pb-8" />
          <div className="text-xs font-bold text-slate-900 pt-1">
            {order.supplier.name}
          </div>
          <div className="text-[10px] text-slate-500">Aceptación Proveedor</div>
        </div>
      </div>

      {/* Document Footer */}
      <div className="mt-8 pt-3 border-t border-slate-200 text-center text-[10px] text-slate-400">
        Documento oficial generado por OptiPlant • Fecha de impresión:{' '}
        {formatDateTime(new Date().toISOString())}
      </div>
    </div>
  )
}
