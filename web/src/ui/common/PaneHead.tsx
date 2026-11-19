import type { ReactNode } from 'react'
import { Icon } from './Icons'
import { t } from '../../i18n/tr'

/** Detail-pane toolbar: back (or close) on the left, actions on the right. */
export function PaneHead({ canGoBack, onBack, children }: { canGoBack: boolean; onBack: () => void; children?: ReactNode }) {
  return (
    <div className="pane-head">
      <button className="icon-btn" onClick={onBack} aria-label={canGoBack ? t.back : t.close}>
        <Icon name={canGoBack ? 'back' : 'close'} />
      </button>
      <div className="grow" />
      {children}
    </div>
  )
}
