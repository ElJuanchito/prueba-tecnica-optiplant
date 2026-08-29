import { Card, CardContent } from '@/components/ui/card.tsx'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import { useCategories } from '../hooks/use-categories.ts'
import { useProducts } from '../hooks/use-products.ts'
import { CategoryTable } from './CategoryTable.tsx'
import { ProductTable } from './ProductTable.tsx'
import { Boxes, FolderTree, Scale } from 'lucide-react'

interface CatalogDashboardProps {
  onLogout?: (() => void) | undefined
}

export function CatalogDashboard({ onLogout }: CatalogDashboardProps) {
  const sessionQuery = useSession()
  const session = sessionQuery.data

  const role = session?.role ?? 'OPERATOR'

  // Metric queries for quick overview
  const productsQuery = useProducts({ page: 0, size: 1, active: 'all' })
  const categoriesQuery = useCategories({ page: 0, size: 1, active: 'all' })

  const totalProducts = productsQuery.data?.totalElements ?? 0
  const totalCategories = categoriesQuery.data?.totalElements ?? 0

  return (
    <AppLayout activeModule="catalog" onLogout={onLogout}>
      <div className="space-y-6">
        {/* Module Title Banner */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-2 border-b border-slate-200">
          <div>
            <h1 className="text-2xl font-black tracking-tight text-slate-900">
              {Permissions.canManageCatalog(role)
                ? 'Catalog Master Data Management'
                : 'Catalog Product & Category Browser'}
            </h1>
            <p className="text-xs text-slate-600 mt-1">
              Centralized agricultural product definitions, taxonomy hierarchies, and multi-unit conversions.
            </p>
          </div>
        </div>
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
      </div>
    </AppLayout>
  )
}
