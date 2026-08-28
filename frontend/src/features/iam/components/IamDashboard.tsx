import * as React from 'react'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Card, CardContent } from '@/components/ui/card.tsx'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { useAuditLogs } from '../hooks/use-audit.ts'
import { useLogout, useSession } from '../hooks/use-auth.ts'
import { useBranches } from '../hooks/use-branches.ts'
import { useUsers } from '../hooks/use-users.ts'
import { AuditTable } from './AuditTable.tsx'
import { BranchTable } from './BranchTable.tsx'
import { UserTable } from './UserTable.tsx'
import {
  Activity,
  Building2,
  FileText,
  Loader2,
  LogOut,
  MapPin,
  Users,
} from 'lucide-react'

interface IamDashboardProps {
  onLogout?: () => void
}

export function IamDashboard({ onLogout }: IamDashboardProps) {
  const sessionQuery = useSession()
  const logoutMutation = useLogout()
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

  const handleLogout = () => {
    logoutMutation.mutate(undefined, {
      onSuccess: () => {
        onLogout?.()
      },
    })
  }

  const userInitial = session?.username ? session.username.charAt(0).toUpperCase() : 'U'
  const assignedBranchName = session?.branchName
    ? session.branchCode
      ? `${session.branchName} (${session.branchCode})`
      : session.branchName
    : session?.branchId
      ? branchesMap.get(session.branchId) ?? 'Assigned Branch'
      : null

  const isOperator = role === 'OPERATOR'
  const canManageUsers = isAdmin || isBranchManager

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 pb-16">
      {/* Top Corporate Navigation Bar */}
      <header className="sticky top-0 z-40 bg-white border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="h-8 w-8 rounded bg-orange-600 flex items-center justify-center text-white font-bold text-sm tracking-tight">
              OP
            </div>
            <div>
              <div className="flex items-baseline gap-1.5">
                <span className="font-black text-lg text-slate-900 tracking-tight">
                  OptiPlant
                </span>
                <span className="text-[10px] font-bold text-orange-600 uppercase tracking-widest">
                  CONSULTORES
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <div className="hidden sm:flex items-center space-x-2.5 bg-slate-100 px-3 py-1 rounded-md border border-slate-200 text-xs">
              <div className="h-5 w-5 rounded bg-slate-800 text-white flex items-center justify-center font-bold text-[10px]">
                {userInitial}
              </div>
              <span className="font-medium text-slate-800">
                {session?.username ?? 'Authenticated User'}
              </span>
              <Badge
                variant={isAdmin ? 'default' : 'secondary'}
                className="text-[10px] py-0 px-1.5 font-semibold"
              >
                {role}
              </Badge>
              {session?.branchId && (
                <span className="flex items-center text-[11px] text-slate-600 pl-1.5 border-l border-slate-300">
                  <MapPin className="h-3 w-3 mr-1 text-slate-400" />
                  {assignedBranchName}
                </span>
              )}
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={handleLogout}
              disabled={logoutMutation.isPending}
              className="text-xs text-slate-600 hover:text-slate-900 border-slate-300 hover:bg-slate-50 cursor-pointer"
            >
              {logoutMutation.isPending ? (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              ) : (
                <LogOut className="h-3.5 w-3.5 mr-1.5" />
              )}
              {logoutMutation.isPending ? 'Cerrando...' : 'Sign Out'}
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 space-y-6">
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
                Your account is configured with Operator permissions for <strong>{assignedBranchName ?? 'your assigned plant'}</strong>. User directory and administrative governance are managed by Branch Managers and Administrators.
              </p>
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
              <TabsContent value="branches" className="focus-visible:outline-none">
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
      </main>
    </div>
  )
}


