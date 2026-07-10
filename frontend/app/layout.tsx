import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Sidebar } from "@/components/layout/Sidebar";
import { ThemeProvider } from "@/lib/ThemeContext";
import { ThemeScript } from "@/components/ThemeScript";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "RAG",
  description: "Dokumenty i czat",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="pl"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="h-screen w-screen overflow-hidden bg-surface text-ink">
        <ThemeScript />
        <ThemeProvider>
          <div className="app-shell h-full w-full">
            <Sidebar />
            <main className="app-main">
              {children}
            </main>
          </div>
        </ThemeProvider>
      </body>
    </html>
  );
}
