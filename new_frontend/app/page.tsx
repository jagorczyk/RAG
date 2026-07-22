'use client'

import { useState } from 'react'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

export default function Home() {
  const [hoveredItem, setHoveredItem] = useState<string | null>(null)

  const items = [
    { id: 1, name: 'Image Analysis', desc: 'Face detection and identity resolution', color: 'primary' },
    { id: 2, name: 'Graph Query', desc: 'Person relationships and facts', color: 'accent' },
    { id: 3, name: 'Visual Cues', desc: 'Scene details and annotations', color: 'dark' },
  ]

  return (
    <div className="min-h-screen bg-[var(--color-bg)] p-8">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <header className="flex items-center justify-between mb-12">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-[var(--color-primary)] rounded-xl flex items-center justify-center">
              <span className="text-white text-xl font-bold">R</span>
            </div>
            <h1 className="text-3xl font-semibold tracking-tight">RAG Studio</h1>
          </div>
          
          <nav className="flex gap-8 text-sm font-medium">
            <a href="#" className="hover:text-[var(--color-primary)] transition-colors">Dashboard</a>
            <a href="#" className="hover:text-[var(--color-primary)] transition-colors">Images</a>
            <a href="#" className="hover:text-[var(--color-primary)] transition-colors">Knowledge Graph</a>
            <a href="#" className="hover:text-[var(--color-primary)] transition-colors">Settings</a>
          </nav>

          <Button 
            variant="primary"
            onClick={() => alert('New Query started! (Timeless UI demo)')}
          >
            New Query
          </Button>
        </header>

        <div className="grid grid-cols-12 gap-8">
          {/* Sidebar */}
          <aside className="col-span-3 bg-white rounded-3xl p-6 shadow-xl h-fit sticky top-8">
            <div className="space-y-6">
              <div>
                <div className="text-xs uppercase tracking-widest text-[var(--color-text-light)] mb-3 font-mono">MENU</div>
                <div className="space-y-1">
                  {['Home', 'Upload', 'Search', 'Reports'].map((item, i) => (
                    <div 
                      key={i}
                      className="flex items-center gap-3 px-4 py-3 rounded-2xl hover:bg-[var(--color-accent)] cursor-pointer micro-interaction group"
                    >
                      <span className="text-lg group-hover:scale-110 transition-transform">📁</span>
                      <span className="font-medium">{item}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="pt-6 border-t border-[var(--color-accent)]/50">
                <div className="flex items-center gap-3 bg-[var(--color-accent)]/50 rounded-2xl p-4">
                  <div className="w-10 h-10 bg-[var(--color-primary)] rounded-2xl flex items-center justify-center text-white text-xl">👤</div>
                  <div>
                    <div className="font-semibold">Dr. AI</div>
                    <div className="text-xs text-emerald-600">Online • RAG Expert</div>
                  </div>
                </div>
              </div>
            </div>
          </aside>

          {/* Main Content */}
          <main className="col-span-9 space-y-8">
            {/* Hero Section - Visual Hierarchy */}
            <Card>
              <div className="flex justify-between items-end mb-6">
                <div>
                  <div className="inline-flex items-center gap-2 bg-[var(--color-primary)]/10 text-[var(--color-primary)] text-xs font-semibold px-4 py-2 rounded-full mb-2">
                    LIVE • JULY 22 2026
                  </div>
                  <h2 className="text-5xl font-semibold tracking-tighter leading-none mb-2">Knowledge Graph</h2>
                  <p className="text-[var(--color-text-light)] max-w-md">Every fact, face, and relation grounded in your photos</p>
                </div>
                
                <div className="text-right">
                  <div className="text-sm font-medium text-[var(--color-primary)]">42 images • 127 people • 89 facts</div>
                  <div className="h-2 w-32 bg-[var(--color-accent)] rounded-full mt-3 overflow-hidden">
                    <div className="h-full w-3/4 bg-[var(--color-primary)] animate-pulse" />
                  </div>
                </div>
              </div>

              {/* Animated Cards Grid - Visual Hierarchy */}
              <div className="grid grid-cols-3 gap-4">
                {items.map((item, index) => (
                  <div 
                    key={item.id}
                    className={`card p-5 list-item ${hoveredItem === `item-${item.id}` ? 'ring-2 ring-[var(--color-primary)] scale-[1.03]' : ''}`}
                    onMouseEnter={() => setHoveredItem(`item-${item.id}`)}
                    onMouseLeave={() => setHoveredItem(null)}
                    style={{ animationDelay: `${index * 80}ms` }}
                  >
                    <div className="flex items-center gap-4">
                      <div className={`w-12 h-12 rounded-2xl flex items-center justify-center text-3xl shadow-inner ${item.color === 'primary' ? 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]' : item.color === 'accent' ? 'bg-[var(--color-accent)] text-[var(--color-dark)]' : 'bg-[var(--color-dark)] text-white'}`}>
                        {item.color === 'primary' ? '🖼️' : item.color === 'accent' ? '📊' : '🔗'}
                      </div>
                      <div>
                        <div className="font-semibold text-lg">{item.name}</div>
                        <div className="text-xs text-[var(--color-text-light)] mt-0.5">{item.desc}</div>
                      </div>
                    </div>
                    <div className="mt-6 h-1.5 bg-[var(--color-accent)]/30 rounded-full overflow-hidden">
                      <div className="h-full bg-gradient-to-r from-[var(--color-primary)] to-[var(--color-accent)] w-2/3 animate-pulse" />
                    </div>
                  </div>
                ))}
              </div>

              {/* Micro-interaction Overlay Example */}
              <div className="mt-8 flex gap-4">
                <button className="button button-primary px-8 py-3 text-sm rounded-2xl flex items-center gap-2 shadow-md">
                  <span>Run Analysis</span>
                  <span className="text-xs opacity-75">→</span>
                </button>
                
                <button 
                  className="button button-primary px-8 py-3 text-sm rounded-2xl flex items-center gap-2 shadow-md"
                  onClick={() => alert('Animated action triggered!')}
                >
                  <span>Export Report</span>
                </button>
              </div>
            </Card>

            {/* Bottom Section with Overlays */}
            <Card className="relative">
              <div className="flex items-center gap-4 mb-8">
                <div className="text-4xl">📸</div>
                <div>
                  <div className="font-semibold text-xl">Recent Images</div>
                  <div className="text-xs text-[var(--color-text-light)]">Processed with full vision pipeline</div>
                </div>
              </div>

              {/* Simulated animated list with hover micro-interactions */}
              <div className="space-y-3">
                {['Atut Ruczaj', 'Zagumnie', 'Krakow Old Town'].map((name, idx) => (
                  <div 
                    key={idx}
                    className="flex items-center gap-4 bg-[var(--color-accent)]/50 hover:bg-white p-4 rounded-2xl cursor-pointer group transition-all duration-300 list-item"
                    onMouseEnter={() => setHoveredItem(`list-${idx}`)}
                    onMouseLeave={() => setHoveredItem(null)}
                  >
                    <div className="w-6 h-6 bg-white rounded-xl shadow flex items-center justify-center text-xs font-mono text-[var(--color-primary)]">IMG</div>
                    <div className="flex-1">
                      <div className="font-medium">{name}</div>
                      <div className="text-xs text-emerald-500">CONFIRMED • 3 facts</div>
                    </div>
                    <div className={`w-2 h-2 rounded-full ${idx % 2 === 0 ? 'bg-[var(--color-primary)]' : 'bg-emerald-400'}`} />
                    <div className="text-xs font-mono opacity-0 group-hover:opacity-100 transition-all">VIEW</div>
                  </div>
                ))}
              </div>

              {/* Overlay example */}
              <div className="absolute top-8 right-8 bg-white border border-[var(--color-accent)] rounded-3xl p-4 shadow-2xl w-64 text-xs">
                <div className="font-semibold mb-2">AI Insight</div>
                <div className="text-[var(--color-text-light)]">This photo contains 2 confirmed faces with matching clothing details</div>
              </div>
            </Card>
          </main>
        </div>
      </div>
    </div>
  )
}