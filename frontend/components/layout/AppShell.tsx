"use client";

import { usePathname } from "next/navigation";
import { Sidebar } from "./Sidebar";
import { MobileTabBar } from "./MobileTabBar";

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const hideTabBar =
    pathname.startsWith("/chat/") ||
    (pathname.startsWith("/folders/") && pathname !== "/folders") ||
    (pathname.startsWith("/knowledge/") && pathname !== "/knowledge");

  return (
    <div className="app-shell h-full w-full">
      <Sidebar />
      <main className={`app-main ${hideTabBar ? "tab-bar-hidden" : ""}`}>{children}</main>
      <MobileTabBar hidden={hideTabBar} />
    </div>
  );
}
