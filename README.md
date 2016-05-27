# heroQuiz
Alexa Skills / Lambda / Groovy example Super Hero Quiz App

Steps to get running:

- go to AWS console -> Lambda
- new lambda, set name to match what you have in build.gradle
- run the 'deploy' task in gradle
- copy the ARN in upper right corner of your lambda into springSocial.properties key for awsApplicationIds
- go to developer.amazon.com -> Alexa -> Skills -> Create New Skills
- create app name, invocation name
- paste in IntentSchema from /src/main/resources
- paste in sampleutterances from /src/main/resources
- put lambda, enter ARN from AWS console, account linking = no
- put defaults in under privacy
- save
- go back to AWS Console - go to dynamoDB
- set up database, we suggest using import. You'll have to copy the import data from /src/main/resources to an S3 bucket and import from there
- open app on your alexa device 'open hero quiz'
- have fun!
