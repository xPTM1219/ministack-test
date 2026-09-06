# Frontend

The idea is to learn the basics of hosting a webpage with AWS.

## Goal

Frontend webpage hosted in AWS S3 using AWS API Gateway and Lambdas.

### The page

The page should be a VueJS using Typescript webpage that has the following:

* A summary page of the weather the last 3 days from Mongo
  * The weather presented in graphs
  * The Mongo connection has to be accessed via an API Gateway endpoint and use
    a lambda function called `GetWeather`.
    that triggers a lambda function to retrieve the data from Mongo.
* An about page that has the following:
  * My name `Marcial`
  * Email `patoloqsea@yopmail.com`
* A third page that executes the lambda `GetQueue` accessed via
  an API Gateway endpoint.

### Things needed

* The app needs to be hosted in S3,  so `npm build` needs to generate the dist
  folder to copy those files to S3.
* Create an API Gateway Java CDK code in the folder @apigw/ and provide the
  instructions to deploy it.
* Create a Python Lambda inside the folder @lambda/ that goes to Mongo at
  localhost:27017 and does a query to get the weather data.
  * This an example of the weather data in the DB, the database is `xtemp`
    and collection is `weather`.
   ```json
   xtemp> db.weather.findOne()
      {
      _id: ObjectId('696589925829dfb8baeac34f'),
      weather: {
         '2026-01-12': {
            '18:53:54': { description: 'broken clouds', temp: '38.75' },
            '18:53:55': { description: 'broken clouds', temp: '38.75' },
            '19:03:55': { description: 'broken clouds', temp: '38.64' },
            '19:13:55': { description: 'broken clouds', temp: '38.64' },
            '19:23:55': { description: 'few clouds', temp: '37.15' },
            '19:23:56': { description: 'few clouds', temp: '37.15' },
            '19:33:56': { description: 'few clouds', temp: '36.91' },
            '19:43:56': { description: 'clear sky', temp: '36.90' },
            '19:53:56': { description: 'clear sky', temp: '36.18' },
            '19:53:57': { description: 'clear sky', temp: '36.18' },
            '20:03:57': { description: 'clear sky', temp: '35.69' },
            '20:13:57': { description: 'clear sky', temp: '35.69' },
            '20:23:57': { description: 'clear sky', temp: '34.77' },
            '20:23:58': { description: 'clear sky', temp: '34.77' },
            '20:33:58': { description: 'clear sky', temp: '34.41' },
            '20:43:58': { description: 'clear sky', temp: '33.85' },
            '20:53:58': { description: 'clear sky', temp: '33.35' },
            '21:03:59': { description: 'clear sky', temp: '33.30' },
            '21:13:59': { description: 'clear sky', temp: '33.08' },
            '21:23:59': { description: 'clear sky', temp: '33.08' },
            '21:34:00': { description: 'clear sky', temp: '32.16' },
            '21:44:00': { description: 'clear sky', temp: '32.16' },
            '21:54:00': { description: 'clear sky', temp: '31.80' },
            '22:04:01': { description: 'clear sky', temp: '32.02' },
            '22:14:01': { description: 'clear sky', temp: '32.25' },
            '22:24:01': { description: 'clear sky', temp: '32.29' },
            '22:34:02': { description: 'clear sky', temp: '32.29' },
            '22:44:02': { description: 'clear sky', temp: '32.09' },
            '22:54:02': { description: 'clear sky', temp: '32.07' },
            '23:04:03': { description: 'clear sky', temp: '31.87' },
            '23:14:03': { description: 'clear sky', temp: '31.84' },
            '23:24:03': { description: 'clear sky', temp: '31.77' },
            '23:34:04': { description: 'clear sky', temp: '31.77' },
            '23:44:04': { description: 'clear sky', temp: '31.51' },
            '23:54:04': { description: 'clear sky', temp: '31.55' }
         }
      }
      }
   ```
   > The data is updated every 10 minutes but for the webpage it can do it by hour.

* The GetQueue lambda is here @lambda/get_sqs_list.py for reference.
* It should include TypeDoc, ESLint and testing using `Mock Service Worker`
  when running in dev locally.
* The Vuejs project should go inside the folder @s3/frontend/
