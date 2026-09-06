/**
 * Typed API client for the Weather App.
 *
 * All requests go to the REST API Gateway deployed by the `apigw/` CDK stack.
 * The base URL is provided at build time via `VITE_API_BASE_URL`
 * (see `.env.example`).
 */

import type { QueueResponse, WeatherResponse } from '../types/weather'

/** Base URL of the API Gateway stage, e.g. `http://<id>.execute-api.localhost:4566/dev`. */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''

/** Error thrown when an API call fails or returns a non-2xx status. */
export class ApiError extends Error {
  /**
   * @param message  human-readable error description
   * @param status   HTTP status code, if a response was received
   */
  constructor(
    message: string,
    public readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * Perform a GET request and decode the JSON response.
 *
 * @template T expected response type
 * @param path path relative to the API base URL, starting with `/`
 * @returns decoded response body
 * @throws ApiError on network failure or non-2xx status
 */
async function getJson<T>(path: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`)
  } catch (cause) {
    throw new ApiError(`Network error calling ${path}: ${String(cause)}`)
  }
  if (!response.ok) {
    throw new ApiError(`API call ${path} failed`, response.status)
  }
  return (await response.json()) as T
}

/**
 * Fetch hourly-aggregated weather for the last 3 days (GetWeather lambda).
 *
 * @returns weather dates and hourly samples
 * @throws ApiError on failure
 */
export function getWeather(): Promise<WeatherResponse> {
  return getJson<WeatherResponse>('/weather')
}

/**
 * Fetch current SQS queue messages (GetQueue lambda).
 *
 * @returns queue messages
 * @throws ApiError on failure
 */
export function getQueue(): Promise<QueueResponse> {
  return getJson<QueueResponse>('/queue')
}