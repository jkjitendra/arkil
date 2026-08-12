import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Check, Copy, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'

interface ApiKeyDisplayModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  keyName: string
  secretKey: string
  publishableKey?: string
}

export function ApiKeyDisplayModal({
  open,
  onOpenChange,
  keyName,
  secretKey,
  publishableKey,
}: ApiKeyDisplayModalProps) {
  const [copiedSecret, setCopiedSecret] = useState(false)
  const [copiedPublishable, setCopiedPublishable] = useState(false)

  const copyToClipboard = async (text: string, type: 'secret' | 'publishable') => {
    await navigator.clipboard.writeText(text)
    toast.success('Copied to clipboard')
    if (type === 'secret') {
      setCopiedSecret(true)
      setTimeout(() => setCopiedSecret(false), 2000)
    } else {
      setCopiedPublishable(true)
      setTimeout(() => setCopiedPublishable(false), 2000)
    }
  }

  const handleOpenChange = (nextOpen: boolean) => {
    // Reset copy states when closing
    if (!nextOpen) {
      setCopiedSecret(false)
      setCopiedPublishable(false)
    }
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>API Key Created: {keyName}</DialogTitle>
          <DialogDescription>
            Copy your secret key now. You won't be able to see it again!
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Warning */}
          <div className="flex items-start gap-3 bg-amber-500/10 border border-amber-500/20 rounded-lg p-3">
            <AlertTriangle className="h-5 w-5 text-amber-500 flex-shrink-0 mt-0.5" />
            <div className="text-sm">
              <p className="font-medium text-amber-500">Important</p>
              <p className="text-muted-foreground">
                This is the only time your secret key will be shown. Store it securely.
              </p>
            </div>
          </div>

          {/* Secret Key */}
          <div>
            <label className="text-sm font-medium block mb-1.5">Secret Key</label>
            <div className="flex gap-2">
              <Input
                value={secretKey}
                readOnly
                className="font-mono text-sm bg-muted"
              />
              <Button
                type="button"
                variant="outline"
                size="icon"
                onClick={() => copyToClipboard(secretKey, 'secret')}
              >
                {copiedSecret ? (
                  <Check className="h-4 w-4 text-green-500" />
                ) : (
                  <Copy className="h-4 w-4" />
                )}
              </Button>
            </div>
          </div>

          {/* Publishable Key */}
          {publishableKey && (
            <div>
              <label className="text-sm font-medium block mb-1.5">Publishable Key</label>
              <div className="flex gap-2">
                <Input
                  value={publishableKey}
                  readOnly
                  className="font-mono text-sm bg-muted"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  onClick={() => copyToClipboard(publishableKey, 'publishable')}
                >
                  {copiedPublishable ? (
                    <Check className="h-4 w-4 text-green-500" />
                  ) : (
                    <Copy className="h-4 w-4" />
                  )}
                </Button>
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button onClick={() => onOpenChange(false)}>
            I've copied my key
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
