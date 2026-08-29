import { beforeEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nProvider, LanguageSwitcher, useTranslation } from '../i18n/i18n-context.tsx'

function TestConsumer() {
  const { t, language, setLanguage } = useTranslation()
  return (
    <div>
      <span data-testid="current-lang">{language}</span>
      <span data-testid="brand">{t('nav.brand')}</span>
      <span data-testid="inventory">{t('nav.inventory')}</span>
      <span data-testid="custom">{t('common.showing')}</span>
      <span data-testid="interpolation">
        {t('common.pageOf', { page: 1, totalPages: 5 })}
      </span>
      <button onClick={() => setLanguage('en')}>Switch to English</button>
      <button onClick={() => setLanguage('es')}>Switch to Spanish</button>
      <LanguageSwitcher />
    </div>
  )
}

describe('Internationalization (i18n) Module Tests', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('defaults to Spanish (es) as initial language', () => {
    render(
      <I18nProvider>
        <TestConsumer />
      </I18nProvider>,
    )

    expect(screen.getByTestId('current-lang')).toHaveTextContent('es')
    expect(screen.getByTestId('inventory')).toHaveTextContent('Inventario y Stock')
    expect(screen.getByTestId('custom')).toHaveTextContent('Mostrando')
    expect(screen.getByTestId('interpolation')).toHaveTextContent('Página 1 de 5')
  })

  it('switches between Spanish and English interactively and persists selection', async () => {
    const user = userEvent.setup()

    render(
      <I18nProvider>
        <TestConsumer />
      </I18nProvider>,
    )

    expect(screen.getByTestId('current-lang')).toHaveTextContent('es')

    // Switch to English
    await user.click(screen.getByRole('button', { name: 'Switch to English' }))
    expect(screen.getByTestId('current-lang')).toHaveTextContent('en')
    expect(screen.getByTestId('inventory')).toHaveTextContent('Inventory & Stock')
    expect(screen.getByTestId('custom')).toHaveTextContent('Showing')
    expect(screen.getByTestId('interpolation')).toHaveTextContent('Page 1 of 5')
    expect(localStorage.getItem('optiplant_lang')).toBe('en')

    // Switch back to Spanish via LanguageSwitcher button
    await user.click(screen.getByRole('button', { name: 'ES' }))
    expect(screen.getByTestId('current-lang')).toHaveTextContent('es')
    expect(screen.getByTestId('inventory')).toHaveTextContent('Inventario y Stock')
    expect(localStorage.getItem('optiplant_lang')).toBe('es')
  })
})
