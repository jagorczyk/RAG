import type { Metadata } from 'next'
import '../globals.css'

export const metadata: Metadata = {
  title: 'RAG UI Demo',
  description: 'Timeless animated UI for RAG application',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className="bg-[var(--color-bg)] text-[var(--color-text)]">
        {children}
      </body>
    </html>
  )
}