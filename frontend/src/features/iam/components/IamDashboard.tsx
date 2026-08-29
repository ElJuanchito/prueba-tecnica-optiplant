import * as React from 'react'
import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button.tsx'
import { Card, CardContent } from '@/components/ui/card.tsx'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { useAuditLogs } from '../hooks/use-audit.ts'
import { useSession } from '../hooks/use-auth.ts'
import { useBranches } from '../hooks/use-branches.ts'
import { useUsers } from '../hooks/use-users.ts'
import { AuditTable } from './AuditTable.tsx'
import { BranchTable } from './BranchTable.tsx'
import { UserTable } from './UserTable.tsx'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import {
  Activity,
  Boxes,
  Building2,
  FileText,
  Users,
} from 'lucide-react'

interface IamDashboardProps {
  onLogout?: (() => void) | undefined
}

export function IamDashboard({ onLogout }: IamDashboardProps) {
  const sessionQuery = useSession()
  const session = sessionQuery.data

  const role = session?.role ?? 'OPERATOR'
  const isAdmin = role === 'ADMIN'
  const isBranchManager = role === 'BRANCH_MANAGER'

  // Metric queries for quick overview
  const usersQuery = useUsers({ page: 0, size: 1 })
  const branchesQuery = useBranches({ page: 0, size: 100 })
  const auditQuery = useAuditLogs({ page: 0, size: 1 })

  const branchesMap = React.useMemo(() => {
    const map = new Map<string, string>()
    branchesQuery.data?.content?.forEach((b) => {
      map.set(b.externalId, `${b.name} (${b.code})`)
    })
    return map
  }, [branchesQuery.data?.content])

  const totalUsers = usersQuery.data?.totalElements ?? 0
  const totalBranches = branchesQuery.data?.totalElements ?? 0
  const totalAuditLogs = auditQuery.data?.totalElements ?? 0

  const assignedBranchName = session?.branchName
    ? session.branchCode
      ? `${session.branchName} (${session.branchCode})`
      : session.branchName
    : session?.branchId
      ? (branchesMap.get(session.branchId) ?? 'Assigned Branch')
      : null

  const isOperator = role === 'OPERATOR'
  const canManageUsers = isAdmin || isBranchManager

  return (
    <AppLayout activeModule="iam" onLogout={onLogout}>
      <div className="space-y-6">
        {/* Module Title Banner */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-2 border-b border-slate-200">
          <div>
            <h1 className="text-2xl font-black tracking-tight text-slate-900">
              {isAdmin ? 'IAM Governance & Security Dashboard' : 'Branch Governance & Operations'}
            </h1>
            <p className="text-xs text-slate-600 mt-1">
              {isAdmin
                ? 'Multi-branch enterprise identity management, role-based access control (RBAC), and immutable audit trail.'
                : 'Branch operator management and local audit visibility.'}
            </p>
          </div>
        </div>
        {/* Sober Overview Stats */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {canManageUsers ? (
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardContent className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Total Users
                  </p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-2xl font-bold text-slate-900">
                      {usersQuery.isLoading ? '...' : totalUsers}
                    </span>
                    <span className="text-xs text-slate-500">
                      cuentas registradas
                    </span>
                  </div>
                </div>
                <div className="h-9 w-9 rounded bg-slate-100 text-slate-700 flex items-center justify-center border border-slate-200">
                  <Users className="h-4 w-4" />
                </div>
              </CardContent>
            </Card>
          ) : (
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardContent className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Operator Role
                  </p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-lg font-bold text-slate-900">
                      OPERATOR
                    </span>
                    <span className="text-xs text-slate-500">
                      Plant Station
                    </span>
                  </div>
                </div>
                <div className="h-9 w-9 rounded bg-slate-100 text-slate-700 flex items-center justify-center border border-slate-200">
                  <Users className="h-4 w-4" />
                </div>
              </CardContent>
            </Card>
          )}

          {isAdmin && (
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardContent className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Sedes Activas
                  </p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-2xl font-bold text-slate-900">
                      {branchesQuery.isLoading ? '...' : totalBranches}
                    </span>
                    <span className="text-xs text-orange-600 font-medium">
                      Multi-sede
                    </span>
                  </div>
                </div>
                <div className="h-9 w-9 rounded bg-orange-50 text-orange-700 flex items-center justify-center border border-orange-200">
                  <Building2 className="h-4 w-4" />
                </div>
              </CardContent>
            </Card>
          )}

          {(isAdmin || isBranchManager) && (
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardContent className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Registro de Auditoría
                  </p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-2xl font-bold text-slate-900">
                      {auditQuery.isLoading ? '...' : totalAuditLogs}
                    </span>
                    <span className="text-xs text-slate-500">
                      eventos registrados
                    </span>
                  </div>
                </div>
                <div className="h-9 w-9 rounded bg-slate-100 text-slate-700 flex items-center justify-center border border-slate-200">
                  <Activity className="h-4 w-4" />
                </div>
              </CardContent>
            </Card>
          )}

          {isOperator && (
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardContent className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Assigned Location
                  </p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-sm font-semibold text-slate-900">
                      {assignedBranchName ?? 'Corporate'}
                    </span>
                  </div>
                </div>
                <div className="h-9 w-9 rounded bg-orange-50 text-orange-700 flex items-center justify-center border border-orange-200">
                  <Building2 className="h-4 w-4" />
                </div>
              </CardContent>
            </Card>
          )}
        </div>

        {/* Operator Restricted View */}
        {isOperator ? (
          <div className="bg-white border border-slate-200 rounded-lg p-8 text-center space-y-4 shadow-2xs">
            <div className="h-12 w-12 rounded-full bg-orange-50 text-orange-700 flex items-center justify-center mx-auto border border-orange-200">
              <Users className="h-6 w-6" />
            </div>
            <div className="max-w-md mx-auto space-y-1.5">
              <h3 className="text-base font-bold text-slate-900">
                Plant Operator Session
              </h3>
              <p className="text-xs text-slate-500 leading-relaxed">
                Your account is configured with Operator permissions for{' '}
                <strong>{assignedBranchName ?? 'your assigned plant'}</strong>.
                User directory and administrative governance are managed by
                Branch Managers and Administrators.
              </p>
              <div className="pt-2">
                <Link to="/catalog">
                  <Button
                    size="sm"
                    className="text-xs bg-indigo-700 hover:bg-indigo-800 text-white cursor-pointer"
                  >
                    <Boxes className="h-3.5 w-3.5 mr-1.5" />
                    Browse Catalog Master Data
                  </Button>
                </Link>
              </div>
            </div>
          </div>
        ) : (
          /* Navigation Tabs for ADMIN and BRANCH_MANAGER */
          <Tabs defaultValue="users" className="space-y-4">
            <TabsList className="bg-white border border-slate-200 p-0.5 rounded-md h-auto">
              <TabsTrigger
                value="users"
                className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-slate-900 data-[state=active]:text-white transition-colors"
              >
                <Users className="h-3.5 w-3.5" />
                <span>Users</span>
              </TabsTrigger>

              {isAdmin && (
                <TabsTrigger
                  value="branches"
                  className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-slate-900 data-[state=active]:text-white transition-colors"
                >
                  <Building2 className="h-3.5 w-3.5" />
                  <span>Branches</span>
                </TabsTrigger>
              )}

              {(isAdmin || isBranchManager) && (
                <TabsTrigger
                  value="audit"
                  className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-slate-900 data-[state=active]:text-white transition-colors"
                >
                  <FileText className="h-3.5 w-3.5" />
                  <span>Audit Log</span>
                </TabsTrigger>
              )}
            </TabsList>

            <TabsContent value="users" className="focus-visible:outline-none">
              <UserTable
                currentActorRole={role}
                currentActorBranchId={session?.branchId ?? null}
              />
            </TabsContent>

            {isAdmin && (
              <TabsContent
                value="branches"
                className="focus-visible:outline-none"
              >
                <BranchTable />
              </TabsContent>
            )}

            {(isAdmin || isBranchManager) && (
              <TabsContent value="audit" className="focus-visible:outline-none">
                <AuditTable />
              </TabsContent>
            )}
          </Tabs>
        )}
      </div>
    </AppLayout>
  )
}
