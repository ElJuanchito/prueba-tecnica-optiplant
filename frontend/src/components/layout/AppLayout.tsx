import * as React from 'react'
import { Link } from '@tanstack/react-router'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { useLogout, useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import { AlertBadge } from '@/features/notifications/components/AlertBadge.tsx'
import { LanguageSwitcher, useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  ArrowLeftRight,
  BadgePercent,
  BellRing,
  Boxes,
  ChevronLeft,
  ChevronRight,
  LogOut,
  MapPin,
  Menu,
  Package,
  Route as RouteIcon,
  ShieldCheck,
  ShoppingCart,
  Users,
  X,
} from 'lucide-react'

interface AppLayoutProps {
  children: React.ReactNode
  activeModule?:
    | 'iam'
    | 'inventory'
    | 'notifications'
    | 'catalog'
    | 'transfers'
    | 'logistics'
    | 'sales'
    | 'pricing'
    | 'customers'
    | undefined
  onLogout?: (() => void) | undefined
}

export function AppLayout({
  children,
  activeModule,
  onLogout,
}: AppLayoutProps) {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const logoutMutation = useLogout()
  const currentPath =
    typeof window !== 'undefined' ? window.location.pathname || '/' : '/'

  const session = sessionQuery.data
  const role = session?.role ?? 'OPERATOR'
  const isAdmin = role === 'ADMIN'

  // Sidebar collapse state with localStorage persistence
  const [isCollapsed, setIsCollapsed] = React.useState<boolean>(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('optiplant_sidebar_collapsed') === 'true'
    }
    return false
  })

  // Mobile sidebar open/close state
  const [isMobileOpen, setIsMobileOpen] = React.useState(false)

  const toggleCollapse = () => {
    setIsCollapsed((prev) => {
      const next = !prev
      if (typeof window !== 'undefined') {
        localStorage.setItem('optiplant_sidebar_collapsed', String(next))
      }
      return next
    })
  }

  const handleLogout = () => {
    logoutMutation.mutate(undefined, {
      onSuccess: () => {
        onLogout?.()
      },
    })
  }

  const userInitial = session?.username
    ? session.username.charAt(0).toUpperCase()
    : 'U'

  const assignedBranchName = session?.branchName
    ? session.branchCode
      ? `${session.branchName} (${session.branchCode})`
      : session.branchName
    : session?.branchId
      ? 'Assigned Branch'
      : null

  // Determine navigation items filtered by RBAC
  const navItems = [
    {
      id: 'iam',
      label: isAdmin ? t('nav.iam') : t('iam.title'),
      shortLabel: 'IAM',
      path: '/',
      icon: ShieldCheck,
      visible: Permissions.canAccessIam(role),
      badge: isAdmin ? 'Governance' : 'Branch',
      color: 'text-orange-600 group-hover:text-orange-700',
      activeBg: 'bg-orange-50 text-orange-900 border-orange-200',
    },
    {
      id: 'inventory',
      label: t('nav.inventory'),
      shortLabel: 'Inventory',
      path: '/inventory',
      icon: Boxes,
      visible: true,
      badge: null,
      color: 'text-amber-600 group-hover:text-amber-700',
      activeBg: 'bg-amber-50 text-amber-900 border-amber-200',
    },
    {
      id: 'notifications',
      label: t('nav.alerts'),
      shortLabel: 'Alerts',
      path: '/notifications',
      icon: BellRing,
      visible: Permissions.canAccessAlerts(role),
      badge: <AlertBadge />,
      color: 'text-rose-600 group-hover:text-rose-700',
      activeBg: 'bg-rose-50 text-rose-900 border-rose-200',
    },
    {
      id: 'catalog',
      label: Permissions.canManageCatalog(role)
        ? t('nav.catalog')
        : t('nav.catalogBrowser'),
      shortLabel: 'Catalog',
      path: '/catalog',
      icon: Package,
      visible: true,
      badge: Permissions.canManageCatalog(role) ? 'Master' : 'View',
      color: 'text-indigo-600 group-hover:text-indigo-700',
      activeBg: 'bg-indigo-50 text-indigo-900 border-indigo-200',
    },
    {
      id: 'transfers',
      label: t('nav.transfers'),
      shortLabel: 'Transfers',
      path: '/transfers',
      icon: ArrowLeftRight,
      visible: Permissions.canAccessTransfers(role),
      badge: null,
      color: 'text-cyan-600 group-hover:text-cyan-700',
      activeBg: 'bg-cyan-50 text-cyan-900 border-cyan-200',
    },
    {
      id: 'logistics',
      label: t('nav.logistics'),
      shortLabel: 'Logistics',
      path: '/logistics',
      icon: RouteIcon,
      visible: Permissions.canAccessLogistics(role),
      badge: isAdmin ? 'Network' : 'Monitor',
      color: 'text-emerald-600 group-hover:text-emerald-700',
      activeBg: 'bg-emerald-50 text-emerald-900 border-emerald-200',
    },
    {
      id: 'sales',
      label: t('nav.sales'),
      shortLabel: 'Sales',
      path: '/sales',
      icon: ShoppingCart,
      visible: Permissions.canAccessSales(role),
      badge: null,
      color: 'text-teal-600 group-hover:text-teal-700',
      activeBg: 'bg-teal-50 text-teal-900 border-teal-200',
    },
    {
      id: 'pricing',
      label: t('nav.pricing'),
      shortLabel: 'Pricing',
      path: '/pricing',
      icon: BadgePercent,
      visible: Permissions.canAccessPricing(role),
      badge: isAdmin ? 'Admin' : 'View',
      color: 'text-violet-600 group-hover:text-violet-700',
      activeBg: 'bg-violet-50 text-violet-900 border-violet-200',
    },
    {
      id: 'customers',
      label: t('nav.customers'),
      shortLabel: 'Customers',
      path: '/customers',
      icon: Users,
      visible: Permissions.canAccessCustomers(role),
      badge: null,
      color: 'text-sky-600 group-hover:text-sky-700',
      activeBg: 'bg-sky-50 text-sky-900 border-sky-200',
    },
  ].filter((item) => item.visible)

  const isCurrentActive = (itemPath: string, itemId: string) => {
    if (activeModule) return activeModule === itemId
    if (itemPath === '/' && currentPath === '/') return true
    if (itemPath !== '/' && currentPath.startsWith(itemPath)) return true
    return false
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col md:flex-row">
      {/* Mobile Backdrop */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-slate-900/60 backdrop-blur-xs md:hidden"
          onClick={() => setIsMobileOpen(false)}
          onKeyDown={(e) => e.key === 'Escape' && setIsMobileOpen(false)}
          tabIndex={-1}
          role="presentation"
        />
      )}

      {/* Sidebar Navigation */}
      <aside
        className={`fixed md:sticky top-0 z-50 h-screen bg-white border-r border-slate-200 transition-all duration-300 flex flex-col justify-between shrink-0 shadow-xs ${
          isMobileOpen
            ? 'translate-x-0 w-72'
            : '-translate-x-full md:translate-x-0'
        } ${isCollapsed ? 'md:w-20' : 'md:w-64'}`}
      >
        {/* Top Header / Brand Logo */}
        <div>
          <div className="h-16 border-b border-slate-100 flex items-center justify-between px-4">
            <Link
              to="/"
              className="flex items-center gap-3 overflow-hidden text-left focus:outline-none"
              onClick={() => setIsMobileOpen(false)}
            >
              <div className="h-9 w-9 rounded-xl bg-gradient-to-br from-amber-500 via-orange-600 to-indigo-600 flex items-center justify-center text-white font-black text-sm tracking-tight shadow-xs shrink-0">
                OP
              </div>
              {(!isCollapsed || isMobileOpen) && (
                <div className="truncate">
                  <div className="flex items-baseline gap-1">
                    <span className="font-black text-base text-slate-900 tracking-tight">
                      OptiPlant
                    </span>
                  </div>
                  <p className="text-[9px] font-extrabold text-orange-600 uppercase tracking-widest leading-none">
                    {t('nav.brandSubtitle')}
                  </p>
                </div>
              )}
            </Link>

            {/* Mobile close button */}
            <button
              type="button"
              onClick={() => setIsMobileOpen(false)}
              className="md:hidden h-8 w-8 rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-100 flex items-center justify-center"
              aria-label="Close sidebar"
            >
              <X className="h-5 w-5" />
            </button>

            {/* Desktop collapse toggle button */}
            <button
              type="button"
              onClick={toggleCollapse}
              className="hidden md:flex h-7 w-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 items-center justify-center transition-colors"
              title={
                isCollapsed ? t('nav.expandSidebar') : t('nav.collapseSidebar')
              }
              aria-label={
                isCollapsed ? t('nav.expandSidebar') : t('nav.collapseSidebar')
              }
            >
              {isCollapsed ? (
                <ChevronRight className="h-4 w-4" />
              ) : (
                <ChevronLeft className="h-4 w-4" />
              )}
            </button>
          </div>

          {/* Navigation Links */}
          <div className="px-3 py-4 space-y-1.5 overflow-y-auto max-h-[calc(100vh-14rem)]">
            {(!isCollapsed || isMobileOpen) && (
              <p className="px-2.5 pb-2 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                {t('nav.modules')}
              </p>
            )}

            {navItems.map((item) => {
              const active = isCurrentActive(item.path, item.id)
              const Icon = item.icon
              return (
                <Link
                  key={item.id}
                  to={item.path}
                  onClick={() => setIsMobileOpen(false)}
                  className={`group flex items-center gap-3 px-3 py-2.5 rounded-xl text-xs font-semibold transition-all border ${
                    active
                      ? `${item.activeBg} font-bold shadow-2xs`
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80 border-transparent'
                  } ${isCollapsed && !isMobileOpen ? 'justify-center px-2' : ''}`}
                  title={isCollapsed ? item.label : undefined}
                >
                  <Icon
                    className={`h-4 w-4 shrink-0 transition-colors ${
                      active ? 'text-current' : item.color
                    }`}
                  />
                  {(!isCollapsed || isMobileOpen) && (
                    <span className="flex-1 truncate">{item.label}</span>
                  )}
                  {(!isCollapsed || isMobileOpen) && item.badge && (
                    <span className="shrink-0">
                      {typeof item.badge === 'string' ? (
                        <span className="text-[10px] font-medium bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded border border-slate-200">
                          {item.badge}
                        </span>
                      ) : (
                        item.badge
                      )}
                    </span>
                  )}
                </Link>
              )
            })}
          </div>
        </div>

        {/* Bottom Section: Language & User Profile */}
        <div className="p-3 border-t border-slate-200 bg-slate-50/50 space-y-3">
          {/* Language Switcher */}
          <div
            className={`flex items-center ${isCollapsed && !isMobileOpen ? 'justify-center' : 'justify-between px-1'}`}
          >
            {(!isCollapsed || isMobileOpen) && (
              <span className="text-[11px] font-semibold text-slate-500">
                {t('nav.language')}
              </span>
            )}
            <LanguageSwitcher />
          </div>

          {/* User Profile Card */}
          <div
            className={`flex items-center gap-2.5 p-2 rounded-xl bg-white border border-slate-200 shadow-2xs ${
              isCollapsed && !isMobileOpen ? 'justify-center p-1.5' : ''
            }`}
          >
            <div className="h-8 w-8 rounded-lg bg-slate-900 text-white font-bold text-xs flex items-center justify-center shrink-0">
              {userInitial}
            </div>

            {(!isCollapsed || isMobileOpen) && (
              <div className="flex-1 min-w-0">
                <p className="text-xs font-bold text-slate-900 truncate">
                  {session?.username ?? 'Anonymous'}
                </p>
                <div className="flex items-center gap-1 mt-0.5">
                  <Badge
                    variant="outline"
                    className="text-[9px] py-0 px-1 font-mono font-semibold bg-slate-50 text-slate-700 border-slate-300"
                  >
                    {role}
                  </Badge>
                  {assignedBranchName && (
                    <span className="text-[10px] text-slate-500 truncate flex items-center gap-0.5">
                      <MapPin className="h-2.5 w-2.5 shrink-0" />
                      {assignedBranchName}
                    </span>
                  )}
                </div>
              </div>
            )}

            <Button
              variant="ghost"
              size="sm"
              onClick={handleLogout}
              className="h-7 w-7 p-0 text-slate-400 hover:text-rose-600 hover:bg-rose-50 shrink-0"
              title={t('nav.logout')}
            >
              <LogOut className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </aside>

      {/* Main App Content Area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Mobile Header Bar */}
        <header className="md:hidden sticky top-0 z-30 bg-white border-b border-slate-200 h-14 px-4 flex items-center justify-between shadow-2xs">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setIsMobileOpen(true)}
              className="h-9 w-9 rounded-lg border border-slate-200 text-slate-700 flex items-center justify-center hover:bg-slate-50"
              aria-label="Open sidebar menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <span className="font-bold text-sm text-slate-900">
              {t('nav.brand')}
            </span>
          </div>

          <div className="flex items-center gap-2">
            <LanguageSwitcher />
            <div className="h-7 w-7 rounded-md bg-slate-900 text-white font-bold text-xs flex items-center justify-center">
              {userInitial}
            </div>
          </div>
        </header>

        {/* Content Children */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          {children}
        </main>
      </div>
    </div>
  )
}
