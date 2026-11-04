import { useRef, useState, type DragEvent } from 'react'
import { api, ApiError } from '../api'
import { isPdf } from '../pdfFile'
import { useLive } from '../live'
import { useToast } from '../toast'

const MAX_FILE = 10 * 1024 * 1024
const MAX_BATCH = 100 * 1024 * 1024

export function UploadDrop({ jobId }: { jobId: string }) {
  const toast = useToast()
  const { refresh } = useLive()
  const inputRef = useRef<HTMLInputElement>(null)
  const depth = useRef(0)
  const [over, setOver] = useState(false)
  const [busy, setBusy] = useState(false)

  async function send(list: File[]) {
    const files = Array.from(list)
    if (files.length === 0) return
    const problem = validate(files)
    if (problem) {
      toast.error(problem)
      return
    }
    setBusy(true)
    try {
      const result = await api.upload(jobId, files)
      const count = result.candidate_ids.length
      toast.success(`${count} CV${count === 1 ? '' : 's'} entered the pipeline.`)
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : 'The upload failed.')
      return
    } finally {
      setBusy(false)
      if (inputRef.current) inputRef.current.value = ''
    }
    try {
      await refresh()
    } catch {
      // The live poll reports a sustained API failure on its own.
    }
  }

  function onDragEnter(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    depth.current += 1
    setOver(true)
  }

  function onDragLeave(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    depth.current -= 1
    if (depth.current <= 0) {
      depth.current = 0
      setOver(false)
    }
  }

  return (
    <label
      className={over ? 'drop drop-over' : 'drop'}
      onDragEnter={onDragEnter}
      onDragOver={(event) => event.preventDefault()}
      onDragLeave={onDragLeave}
      onDrop={(event) => {
        event.preventDefault()
        depth.current = 0
        setOver(false)
        if (!busy) void send(Array.from(event.dataTransfer.files))
      }}
    >
      <input
        ref={inputRef}
        type="file"
        accept="application/pdf,.pdf"
        multiple
        disabled={busy}
        onChange={(event) => {
          if (event.target.files) void send(Array.from(event.target.files))
        }}
      />
      <strong>{busy ? 'Sending the batch…' : 'Drop CV PDFs here'}</strong>
      <span>or choose files. PDF only, 10MB each. Every file becomes its own row.</span>
    </label>
  )
}

function validate(files: File[]): string | null {
  const wrong = files.filter((file) => !isPdf(file))
  if (wrong.length > 0) {
    return `Only PDF files can enter the pipeline. Remove ${wrong.map((file) => file.name).join(', ')}.`
  }
  const empty = files.filter((file) => file.size === 0)
  if (empty.length > 0) return `${empty.map((file) => file.name).join(', ')} ${empty.length === 1 ? 'is' : 'are'} empty.`
  const oversized = files.filter((file) => file.size > MAX_FILE)
  if (oversized.length > 0) {
    return `${oversized.map((file) => file.name).join(', ')} ${oversized.length === 1 ? 'exceeds' : 'exceed'} the 10MB limit.`
  }
  const total = files.reduce((sum, file) => sum + file.size, 0)
  if (total > MAX_BATCH) return 'This batch is over 100MB. Upload fewer files.'
  return null
}

