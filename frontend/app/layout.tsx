import type { Metadata } from "next";
import { Fraunces, Source_Sans_3 } from "next/font/google";
import "./globals.css";
import { AppShell } from "@/components/layout/AppShell";
import { ThemeProvider } from "@/lib/ThemeContext";
import { ThemeScript } from "@/components/ThemeScript";

const sourceSans = Source_Sans_3({
  variable: "--font-source-sans",
  subsets: ["latin", "latin-ext"],
  weight: ["400", "500", "600", "700"],
});

const fraunces = Fraunces({
  variable: "--font-fraunces",
  subsets: ["latin", "latin-ext"],
  weight: ["500", "600", "700"],
});

export const metadata: Metadata = {
  title: "RAG",
  description: "Biblioteka zdjęć i czat GraphRAG",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="pl"
      className={`${sourceSans.variable} ${fraunces.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="h-screen w-screen overflow-hidden bg-surface font-sans text-ink">
        <ThemeScript />
        <ThemeProvider>
          <AppShell>{children}</AppShell>
        </ThemeProvider>
      </body>
    </html>
  );
}
