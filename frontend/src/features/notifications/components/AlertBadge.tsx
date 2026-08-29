import { Badge } from '@/components/ui/badge.tsx'
import { useAlerts } from '../hooks/use-alerts.ts'
import { Bell } from 'lucide-react'

interface AlertBadgeProps {
  className?: string
}

export function AlertBadge({ className = '' }: AlertBadgeProps) {
  const alertsQuery = useAlerts({ resolved: false, page: 0, size: 100 })
  const alerts = alertsQuery.data?.content ?? []
  const count = alerts.length
  const hasCritical = alerts.some((a) => a.severity === 'CRITICAL')

  return (
    <div className={`relative flex items-center ${className}`}>
      <Bell
        className={`h-4 w-4 ${
          count > 0
            ? hasCritical
              ? 'text-rose-600 animate-pulse'
              : 'text-amber-600'
            : 'text-slate-400'
        }`}
      />
      {count > 0 && (
        <Badge
          className={`absolute -top-2 -right-2 text-[9px] font-mono h-4 min-w-4 px-1 flex items-center justify-center rounded-full ${
            hasCritical
              ? 'bg-rose-600 text-white'
              : 'bg-amber-600 text-white'
          }`}
        >
          {count > 99 ? '99+' : count}
        </Badge>
      )}
    </div>
  )
}
