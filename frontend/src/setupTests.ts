// Matcher di jest-dom (toBeInTheDocument, toBeDisabled, ...) e pulizia del DOM dopo ogni test
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})
