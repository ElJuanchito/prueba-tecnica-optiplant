import { createRootRoute, Outlet } from '@tanstack/react-router'

export const Route = createRootRoute({
  component: function RootComponent() {
    return (
      <div className="min-h-screen bg-zinc-50 text-zinc-950 antialiased font-sans">
        <Outlet />
      </div>
    )
  },
})
