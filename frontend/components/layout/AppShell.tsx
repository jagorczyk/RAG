"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Sidebar } from "./Sidebar";
import { MobileTabBar } from "./MobileTabBar";
import { isAuthenticated } from "@/lib/auth";

const AUTH_PATHS = ["/login", "/register"];
const PUBLIC_PATHS = ["/privacy"];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const isAuthScreen = AUTH_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
  const isPublicScreen = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (isAuthScreen) {
      if (isAuthenticated()) {
        router.replace("/");
        return;
      }
      setReady(true);
      return;
    }

    if (isPublicScreen) {
      setReady(true);
      return;
    }

    if (!isAuthenticated()) {
      const next = encodeURIComponent(pathname || "/");
      router.replace(`/login?next=${next}`);
      return;
    }
    setReady(true);
  }, [pathname, isAuthScreen, isPublicScreen, router]);

  if (isAuthScreen || isPublicScreen) {
    return (
      <div className="h-full w-full">
        <main id="main-content" tabIndex={-1} className="h-full outline-none">
          {children}
        </main>
      </div>
    );
  }

  if (!ready) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-surface text-sm text-ink-muted">
        Ładowanie…
      </div>
    );
  }

  const hideTabBar =
    pathname.startsWith("/chat/") ||
    (pathname.startsWith("/folders/") && pathname !== "/folders") ||
    (pathname.startsWith("/knowledge/") && pathname !== "/knowledge");

  return (
    <div className="app-shell h-full w-full">
      <a href="#main-content" className="skip-link">
        Przejdź do treści
      </a>
      <Sidebar />
      <main
        id="main-content"
        tabIndex={-1}
        className={`app-main outline-none ${hideTabBar ? "tab-bar-hidden" : ""}`}
      >
        {children}
      </main>
      <MobileTabBar hidden={hideTabBar} />
    </div>
  );
}
