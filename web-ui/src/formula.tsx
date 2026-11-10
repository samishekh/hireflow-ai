import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from './api'
import type { Formula } from './types'

type FormulaContextValue = {
  formula: Formula | null
  setFormula: (formula: Formula) => void
}

const FormulaContext = createContext<FormulaContextValue | null>(null)

export function FormulaProvider({ children }: { children: ReactNode }) {
  const [formula, setFormula] = useState<Formula | null>(null)

  useEffect(() => {
    let cancel = false
    api.formula()
      .then((next) => {
        if (!cancel) setFormula(next)
      })
      .catch(() => {
        // The formula page reports a failed load itself.
      })
    return () => {
      cancel = true
    }
  }, [])

  return <FormulaContext.Provider value={{ formula, setFormula }}>{children}</FormulaContext.Provider>
}

export function useFormula(): Formula | null {
  return useContext(FormulaContext)?.formula ?? null
}

export function useSetFormula(): (formula: Formula) => void {
  const context = useContext(FormulaContext)
  if (!context) throw new Error('FormulaProvider is missing')
  return context.setFormula
}
