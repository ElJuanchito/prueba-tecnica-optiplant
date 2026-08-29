import { Link } from '@tanstack/react-router'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Card, CardContent } from '@/components/ui/card.tsx'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { useLogout, useSession } from '@/features/iam/hooks/use-auth.ts'
import { useCategories } from '../hooks/use-categories.ts'
import { useProducts } from '../hooks/use-products.ts'
import { CategoryTable } from './CategoryTable.tsx'
import { ProductTable } from './ProductTable.tsx'
import { Boxes, FolderTree, Loader2, LogOut, MapPin, Scale } from 'lucide-react'

interface CatalogDashboardProps {
  onLogout?: () => void
}

export function CatalogDashboard({ onLogout }: CatalogDashboardProps) {
  const sessionQuery = useSession()
  const logoutMutation = useLogout()
  const session = sessionQuery.data

  const role = session?.role ?? 'OPERATOR'
  const isAdmin = role === 'ADMIN'

  // Metric queries for quick overview
  const productsQuery = useProducts({ page: 0, size: 1, active: 'all' })
  const categoriesQuery = useCategories({ page: 0, size: 1, active: 'all' })

  const totalProducts = productsQuery.data?.totalElements ?? 0
  const totalCategories = categoriesQuery.data?.totalElements ?? 0

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

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 pb-16">
      {/* Top Corporate Navigation Bar */}
      <header className="sticky top-0 z-40 bg-white border-b border-slate-200 shadow-2xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-6">
            <Link to="/" className="flex items-center space-x-3">
              <div className="h-8 w-8 rounded bg-indigo-600 flex items-center justify-center text-white font-bold text-sm tracking-tight">
                OP
              </div>
              <div>
                <div className="flex items-baseline gap-1.5">
                  <span className="font-black text-lg text-slate-900 tracking-tight">
                    OptiPlant
                  </span>
                  <span className="text-[10px] font-bold text-indigo-600 uppercase tracking-widest">
                    CATÁLOGO
                  </span>
                </div>
              </div>
            </Link>

            <nav className="hidden md:flex items-center space-x-2">
              <Link
                to="/catalog"
                className="text-xs font-bold text-indigo-600 bg-indigo-50 px-2.5 py-1 rounded-md"
              >
                Catalog Master Data
              </Link>
              <Link
                to="/"
                className="text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 px-2.5 py-1 rounded-md transition-colors"
              >
                IAM & Governance
              </Link>
            </nav>
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
          <Card className="bg-white border-slate-200 shadow-2xs">
            <CardContent className="p-4 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Total Products
                </p>
                <div className="flex items-baseline gap-2 mt-1">
                  <span className="text-2xl font-bold text-slate-900">
                    {productsQuery.isLoading ? '...' : totalProducts}
                  </span>
                  <span className="text-xs text-slate-500">
                    SKUs registered
                  </span>
                </div>
              </div>
              <div className="h-9 w-9 rounded bg-indigo-50 text-indigo-700 flex items-center justify-center border border-indigo-200">
                <Boxes className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          <Card className="bg-white border-slate-200 shadow-2xs">
            <CardContent className="p-4 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Product Categories
                </p>
                <div className="flex items-baseline gap-2 mt-1">
                  <span className="text-2xl font-bold text-slate-900">
                    {categoriesQuery.isLoading ? '...' : totalCategories}
                  </span>
                  <span className="text-xs text-emerald-600 font-medium">
                    Master Groups
                  </span>
                </div>
              </div>
              <div className="h-9 w-9 rounded bg-emerald-50 text-emerald-700 flex items-center justify-center border border-emerald-200">
                <FolderTree className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          <Card className="bg-white border-slate-200 shadow-2xs">
            <CardContent className="p-4 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Corporate Scope
                </p>
                <div className="flex items-baseline gap-2 mt-1">
                  <span className="text-sm font-bold text-slate-900">
                    Multi-Unit Conversions
                  </span>
                </div>
                <p className="text-[11px] text-slate-500 mt-0.5">
                  Single source of truth across all branches
                </p>
              </div>
              <div className="h-9 w-9 rounded bg-amber-50 text-amber-700 flex items-center justify-center border border-amber-200">
                <Scale className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Navigation Tabs for Products and Categories */}
        <Tabs defaultValue="products" className="space-y-4">
          <TabsList className="bg-white border border-slate-200 p-0.5 rounded-md h-auto">
            <TabsTrigger
              value="products"
              className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-indigo-900 data-[state=active]:text-white transition-colors"
            >
              <Boxes className="h-3.5 w-3.5" />
              <span>Products</span>
            </TabsTrigger>

            <TabsTrigger
              value="categories"
              className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-indigo-900 data-[state=active]:text-white transition-colors"
            >
              <FolderTree className="h-3.5 w-3.5" />
              <span>Categories</span>
            </TabsTrigger>
          </TabsList>

          <TabsContent value="products" className="focus-visible:outline-none">
            <ProductTable currentActorRole={role} />
          </TabsContent>

          <TabsContent
            value="categories"
            className="focus-visible:outline-none"
          >
            <CategoryTable currentActorRole={role} />
          </TabsContent>
        </Tabs>
      </main>
    </div>
  )
}
