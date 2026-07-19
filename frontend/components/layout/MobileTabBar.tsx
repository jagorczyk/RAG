"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FolderOpen, Users, MessageCircle } from "lucide-react";
import { motion } from "motion/react";

const tabs = [
  { href: "/folders", label: "Biblioteka", icon: FolderOpen, match: (p: string) => p.startsWith("/folders") },
  { href: "/knowledge", label: "Osoby", icon: Users, match: (p: string) => p.startsWith("/knowledge") },
  { href: "/chats", label: "Rozmowy", icon: MessageCircle, match: (p: string) => p.startsWith("/chats") || p.startsWith("/chat") },
];

export function MobileTabBar({ hidden = false }: { hidden?: boolean }) {
  const pathname = usePathname();

  if (hidden) return null;

  return (
    <nav className="mobile-tab-bar" aria-label="Główna nawigacja">
      {tabs.map((tab) => {
        const active = tab.match(pathname);
        const Icon = tab.icon;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            className={`mobile-tab ${active ? "mobile-tab-active" : ""}`}
            aria-current={active ? "page" : undefined}
          >
            {active && (
              <motion.span
                layoutId="mobile-tab-glow"
                className="absolute inset-0 rounded-2xl bg-soft"
                transition={{ type: "spring", stiffness: 420, damping: 34 }}
                aria-hidden
              />
            )}
            <Icon
              size={22}
              className="relative z-10"
              strokeWidth={active ? 2.25 : 1.9}
              aria-hidden
            />
            <span className="relative z-10">{tab.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
