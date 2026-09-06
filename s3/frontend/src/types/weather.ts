/**
 * Shared API response types for the Weather App.
 *
 * These mirror the JSON bodies produced by the `GetWeather` and `GetQueue`
 * lambdas behind the API Gateway.
 */

/** One aggregated hourly weather sample. */
export interface HourSample {
  /** Hour bucket in `HH:00` form. */
  hour: string
  /** Most frequent weather description among the hour's samples. */
  description: string
  /** Average temperature in °C (null when no valid samples). */
  temp: number | null
}

/** GetWeather response: last 3 dates plus hourly samples per date. */
export interface WeatherResponse {
  /** Dates sorted descending (`YYYY-MM-DD`). */
  dates: string[]
  /** Hourly samples keyed by date. */
  weather: Record<string, HourSample[]>
}

/** One SQS message as returned by GetQueue. */
export interface QueueMessage {
  /** SQS message id. */
  messageId: string | null
  /** Message body parsed as JSON when possible, else the raw string. */
  body: unknown
  /** Receipt handle (unused by the UI, included for completeness). */
  receiptHandle?: string | null
}

/** GetQueue response. */
export interface QueueResponse {
  /** Messages currently in the queue. */
  messages: QueueMessage[]
}