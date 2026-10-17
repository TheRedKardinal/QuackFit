import { createAsyncThunk } from '@reduxjs/toolkit'
import { useDispatch, useSelector } from 'react-redux'
import type { DatiErrore } from '../api/ApiError'
import type { AppDispatch, RootState } from './store'

// Hook e thunk tipizzati: i componenti e le feature usano questi, non quelli generici di react-redux.
export const useAppDispatch = useDispatch.withTypes<AppDispatch>()
export const useAppSelector = useSelector.withTypes<RootState>()

/** I thunk rifiutano sempre con DatiErrore (rejectWithValue), così il listener della sessione li riconosce. */
export const createAppAsyncThunk = createAsyncThunk.withTypes<{
  state: RootState
  dispatch: AppDispatch
  rejectValue: DatiErrore
}>()
