import { useState } from 'react'
import { ApiError } from '../api'
import { useToast } from '../toast'

export function ConfirmDelete({
  idle,
  prompt,
  pending,
  success,
  onDelete,
}: {
  idle: string
  prompt: string
  pending: string
  success: string
  onDelete: () => Promise<void>
}) {
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  async function confirm() {
    setBusy(true)
    try {
      await onDelete()
      toast.success(success)
    } catch (caught) {
      const message = caught instanceof ApiError ? caught.message : 'That could not be deleted.'
      toast.error(message)
      setBusy(false)
    }
  }

  if (!open) {
    return (
      <button type="button" className="btn btn-small btn-danger" onClick={() => setOpen(true)}>
        {idle}
      </button>
    )
  }

  return (
    <div className="confirm-row">
      <span>{prompt}</span>
      <button type="button" className="btn btn-small btn-danger armed" disabled={busy} onClick={() => void confirm()}>
        {busy ? pending : 'Delete'}
      </button>
      <button type="button" className="btn btn-small btn-ghost" disabled={busy} onClick={() => setOpen(false)}>
        Cancel
      </button>
    </div>
  )
}
