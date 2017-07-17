package com.vanderfox.hero

import com.amazon.speech.json.SpeechletRequestEnvelope
import com.amazon.speech.slu.Intent
import com.amazon.speech.slu.Slot
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.SpeechletV2
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.speechlet.SupportedInterfaces
import com.amazon.speech.speechlet.interfaces.display.DisplayInterface
import com.amazon.speech.speechlet.interfaces.display.directive.RenderTemplateDirective
import com.amazon.speech.speechlet.interfaces.display.element.Image
import com.amazon.speech.speechlet.interfaces.display.element.ImageInstance
import com.amazon.speech.speechlet.interfaces.display.element.RichText
import com.amazon.speech.speechlet.interfaces.display.template.BodyTemplate1
import com.amazon.speech.speechlet.interfaces.system.SystemInterface
import com.amazon.speech.speechlet.interfaces.system.SystemState
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import com.amazon.speech.ui.SimpleCard
import com.amazon.speech.ui.SsmlOutputSpeech
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.vanderfox.hero.question.Question
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient

/**
 * This app shows how to connect to hero with Spring Social, Groovy, and Alexa.
 * @author Lee Fox and Ryan Vanderwerf
 */
@CompileStatic
class HeroSpeechlet implements SpeechletV2 {
    private static final Logger log = LoggerFactory.getLogger(HeroSpeechlet.class)

    @Override
    void onSessionStarted(SpeechletRequestEnvelope<SessionStartedRequest> requestEnvelope)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
                requestEnvelope.getSession().getSessionId())
        LinkedHashMap<String, Question> askedQuestions = new LinkedHashMap()
        requestEnvelope.getSession().setAttribute("askedQuestions", askedQuestions)
        requestEnvelope.getSession().setAttribute("questionCounter", 5)
        requestEnvelope.getSession().setAttribute("score", 0)
        initializeComponents(requestEnvelope.getSession())
    }

    @Override
    SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> requestEnvelope)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
                requestEnvelope.getSession().getSessionId())

        Boolean supportDisplay = false
        SystemState systemState = requestEnvelope.getContext().getState(SystemInterface.class, SystemState.class)
        SupportedInterfaces supportedInterfaces = systemState.device.getSupportedInterfaces()
        if (supportedInterfaces) {
            supportDisplay = supportedInterfaces.isInterfaceSupported(DisplayInterface)
        }
        log.info("supportDisplay:  " + supportDisplay)
        requestEnvelope.getSession().setAttribute("supportDisplay", supportDisplay)
        getWelcomeResponse(requestEnvelope.getSession())
    }

    static boolean isSupportDisplay(Session session) {
        boolean supportDisplay = (Boolean) session.getAttribute("supportDisplay")
        supportDisplay
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    static SpeechletResponse getWelcomeResponse(Session session) {
        log.info("Welcome message")

        String boldStart = (isSupportDisplay(session)) ? "<b>" : ""
        String boldEnd = (isSupportDisplay(session)) ? "</b>" : ""
        String newLine = (isSupportDisplay(session)) ? "<br/>" : "\n"

        int numberOfQuestions = Integer.parseInt((String) session.getAttribute("questionCounter"))
        String speechText = "Welcome to the Unofficial Star Wars Quiz.  I'm going to ask you " + numberOfQuestions + " questions to test your Star Wars knowledge.  Say repeat question at any time if you need to hear a question again, or say help if you need some help.  To answer a question, just say the number of the answer.  Let's get started:   \n\n"
        String cardText = "Welcome to the Unofficial Star Wars Quiz.  I'm going to ask you " + numberOfQuestions + " questions to test your Star Wars knowledge.  Say " + boldStart + "repeat question" + boldEnd + " at any time if you need to hear a question again, or say " + boldStart + "help" + boldEnd + " if you need some help.  To answer a question, just say the number of the answer.  Let's get started:   " + newLine + newLine
        Question question = getRandomUnaskedQuestion(session)
        session.setAttribute("lastQuestionAsked", question)
        speechText += question.getSpeechText()
        cardText += question.getCardText(isSupportDisplay(session))
        askResponse(cardText, speechText, isSupportDisplay(session))
    }

    @Override
    SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope)
            throws SpeechletException {
        String requestId = requestEnvelope.getRequest().getRequestId()
        Session session = requestEnvelope.getSession()
        log.info("onIntent requestId={}, sessionId={}", requestId,
                session.getSessionId())
        Intent intent = requestEnvelope.getRequest().getIntent()
        String intentName = (intent != null) ? intent.getName() : null
        boolean supportDisplay = isSupportDisplay(session)
        log.debug("Intent = " + intentName)
        switch (intentName) {
            case "AnswerIntent":
                getAnswer(intent.getSlot("Answer"), session, supportDisplay)
                break
            case "DontKnowIntent":
                processAnswer(session, 5, supportDisplay)
                break
            case "AMAZON.HelpIntent":
                getHelpResponse(supportDisplay)
                break
            case "AMAZON.CancelIntent":
                sayGoodbye(supportDisplay)
                break
            case "AMAZON.RepeatIntent":
                repeatQuestion(session, supportDisplay, false)
                break
            case "AMAZON.StopIntent":
                sayGoodbye(supportDisplay)
                break
            default:
                didNotUnderstand(supportDisplay)
                break
        }
    }

    @Override
    void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
                requestEnvelope.getSession().getSessionId())
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    static SpeechletResponse sayGoodbye(boolean supportDisplay) {
        String speechText = "OK.  I'm going to stop the game now."
        tellResponse(speechText, speechText, supportDisplay)
    }

    static Question getRandomUnaskedQuestion(Session session) {
        LinkedHashMap<String, Question> askedQuestions = (LinkedHashMap) session.getAttribute("askedQuestions")
        Question question = getRandomQuestion(session)
        while(askedQuestions.get(question.getQuestion()) != null) {
            question = getRandomQuestion(session)
        }
        askedQuestions.put(question.getQuestion(), question)
        session.setAttribute("askedQuestions", askedQuestions)
        question
    }

    static Question getRandomQuestion(Session session) {
        int tableRowCount = Integer.parseInt((String) session.getAttribute("tableRowCount"))
        int questionIndex = (new Random().nextInt() % tableRowCount).abs()
        log.info("The question index is:  " + questionIndex)
        Question question = getQuestion(questionIndex)
        question
    }

    static Question getQuestion(int questionIndex) {


        DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient())
        Table table = dynamoDB.getTable("StarWarsQuiz")
        Item item = table.getItem("Id", questionIndex)
        def questionText = item.getString("Question")
        def questionAnswer = item.getInt("answer")
        def options = new String[4]
        options[0] = item.getString("option1")
        options[1] = item.getString("option2")
        options[2] = item.getString("option3")
        options[3] = item.getString("option4")
        Question question = new Question()
        question.setQuestion(questionText)
        question.setOptions(options)
        question.setAnswer(questionAnswer)
        question.setIndex(questionIndex)
        log.info("question retrieved index:  " + question.getIndex())
        log.info("question retrieved text:  " + question.getQuestion())
        log.info("question retrieved correct:  " + question.getAnswer())
        log.info("question retrieved number of options:  " + question.getOptions().length)
        question
    }

    static SpeechletResponse askResponse(String cardText, String speechText, boolean supportDisplay) {

        RenderTemplateDirective renderTemplateDirective = buildBodyTemplate1(cardText)

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse response = new SpeechletResponse()

        if (supportDisplay) {
            ArrayList directives = new ArrayList()
            directives.add(renderTemplateDirective)
            response.setDirectives(directives)
        } else {
            SimpleCard card = new SimpleCard()
            card.setTitle("Unofficial Star Wars Quiz")
            card.setContent(cardText)
            response.setCard(card)
        }
        response.setNullableShouldEndSession(false)
        response.setOutputSpeech(speech)
        response.setReprompt(reprompt)
        response

    }

    static RenderTemplateDirective buildBodyTemplate1(String cardText) {
        BodyTemplate1 template = new BodyTemplate1()
        template.setTitle("Unofficial Star Wars Quiz")
        BodyTemplate1.TextContent textContent = new BodyTemplate1.TextContent()
        RichText richText = new RichText()
        richText.text = cardText
        textContent.setPrimaryText(richText)
        template.setTextContent(textContent)
        Image backgroundImage = new Image()
        ImageInstance imageInstance = new ImageInstance()
        imageInstance.setUrl("https://media.giphy.com/media/YJNOIvcwG1NcY/giphy.gif")
        ArrayList<ImageInstance> imageInstances = new ArrayList()
        imageInstances.add(imageInstance)
        backgroundImage.setSources(imageInstances)
        template.setBackgroundImage(backgroundImage)
        RenderTemplateDirective renderTemplateDirective = new RenderTemplateDirective()
        renderTemplateDirective.setTemplate(template)
        renderTemplateDirective
    }

    static SpeechletResponse tellResponse(String cardText, String speechText, boolean supportDisplay) {
        RenderTemplateDirective renderTemplateDirective = buildBodyTemplate1(cardText)

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse response = new SpeechletResponse()
        if (supportDisplay) {
            ArrayList directives = new ArrayList()
            directives.add(renderTemplateDirective)
            response.setDirectives(directives)
        } else {
            SimpleCard card = new SimpleCard()
            card.setTitle("Unofficial Star Wars Quiz")
            card.setContent(cardText)
            response.setCard(card)
        }
        response.setNullableShouldEndSession(true)
        response.setOutputSpeech(speech)
        response.setReprompt(reprompt)
        response


    }

    static SpeechletResponse askResponseFancy(String cardText, String speechText, String fileUrl) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard()
        card.setTitle("Unofficial Star Wars Quiz")
        card.setContent(cardText)

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)
        log.info("making welcome audio")
        SsmlOutputSpeech fancySpeech = new SsmlOutputSpeech()
        fancySpeech.ssml = "<speak><audio src=\"${fileUrl}\"/> ${speechText}</speak>"
        log.info("finished welcome audio")
        // Create reprompt
        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(fancySpeech)

        SpeechletResponse.newAskResponse(fancySpeech, reprompt, card)
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    static SpeechletResponse repeatQuestion(final Session session, boolean supportDisplay, boolean invalidAnswer) {
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        String speechText = ""
        if(invalidAnswer) {
            speechText = "I didn't understand that.  Let's try again.\n\n"
        }
        speechText += question.getSpeechText()
        askResponse(speechText, speechText, supportDisplay)

    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    static SpeechletResponse getAnswer(Slot query, final Session session, boolean supportDisplay) {

        try {
            int guessedAnswer = Integer.parseInt(query.getValue())
            log.info("Guessed answer is:  " + query.getValue())

            return processAnswer(session, guessedAnswer, supportDisplay)
        } catch (NumberFormatException n) {
            return repeatQuestion(session, supportDisplay, true)
        }
    }

    static SpeechletResponse processAnswer(Session session, int guessedAnswer, boolean supportDisplay) {
        def speechText
        def cardText
        String newLine = (isSupportDisplay(session)) ? "<br/>" : "\n"
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        def answer = question.getAnswer()
        log.info("correct answer is:  " + answer)
        log.info("question was:  " + question.getQuestion())

        int questionCounter = decrementQuestionCounter(session)

        if (guessedAnswer == answer) {
            speechText = "You got it right.\n\n"
            cardText = "You got it right." + newLine + newLine
            int score = (Integer) session.getAttribute("score")
            score++
            session.setAttribute("score", score)
            questionMetricsCorrect(question.getIndex())
        } else {
            speechText = "You got it wrong.\n\n"
            cardText = "You got it wrong." + newLine + newLine
            questionMetricsWrong(question.getIndex())
        }

        log.info("questionCounter:  " + questionCounter)

        if (questionCounter > 0) {
            session.setAttribute("state", "askQuestion")
            question = getRandomUnaskedQuestion(session)
            session.setAttribute("lastQuestionAsked", question)
            speechText += question.getSpeechText()
            cardText += question.getCardText(supportDisplay)
            return askResponse(cardText, speechText, supportDisplay)
        } else {
            int score = (Integer) session.getAttribute("score")
            speechText += "\n\nYou answered ${score} questions correctly.\n\nThank you for playing."
            cardText += "You answered ${score} questions correctly.  " + newLine + newLine +"Thank you for playing."
            userMetrics(session.getUser().userId, score)
            return tellResponse(cardText, speechText, supportDisplay)
        }
    }

    static void questionMetricsCorrect(int questionIndex) {
        questionMetrics(questionIndex, true)
    }

    static void questionMetricsWrong(int questionIndex) {
        questionMetrics(questionIndex, false)
    }

    static void questionMetrics(int questionIndex, boolean correct) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient())
        Table table = dynamoDB.getTable("StarWarzQuizMetrics")
        log.debug("getting question id from table ${questionIndex}")
        Item item = table.getItem("id", questionIndex)
        int askedCount = 0
        int correctCount = 0
        if (item != null) {
            askedCount = item.getInt("asked")
            correctCount = item.getInt("correct")
        }
        askedCount++
        if (correct) {
            correctCount++
        }
        Item newItem = new Item()
        newItem.withInt("id", questionIndex)
        newItem.withInt("asked", askedCount)
        newItem.withInt("correct", correctCount)
        table.putItem(newItem)
    }

    static void userMetrics(String userId, int score) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient())
        Table table = dynamoDB.getTable("StarWarsQuizUserMetrics")
        Item item = table.getItem("id", userId)
        int timesPlayed = 0
        int correctCount = 0
        if (item != null) {
            timesPlayed = item.getInt("timesPlayed")
            correctCount = item.getInt("lifeTimeCorrect")
        }
        timesPlayed++
        correctCount += score
        Item newItem = new Item()
        newItem.withString("id", userId)
        newItem.withInt("timesPlayed", timesPlayed)
        newItem.withInt("lifeTimeCorrect", correctCount)
        table.putItem(newItem)
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    static SpeechletResponse getHelpResponse(boolean supportDisplay) {
        String speechText = "You can say stop or cancel to end the game at any time.  If you need a question repeated, say repeat question."
        askResponse(speechText, speechText, supportDisplay)
    }

    static SpeechletResponse didNotUnderstand(boolean supportDisplay) {
        String speechText = "I'm sorry.  I didn't understand what you said.  Say help me for help."
        askResponse(speechText, speechText, supportDisplay)
    }

    static int decrementQuestionCounter(Session session) {
        int questionCounter = (int) session.getAttribute("questionCounter")
        questionCounter--
        session.setAttribute("questionCounter", questionCounter)
        questionCounter

    }

    /**
     * Initializes the instance components if needed.
     */
    static void initializeComponents(Session session) {
        AmazonDynamoDBClient amazonDynamoDBClient
        amazonDynamoDBClient = new AmazonDynamoDBClient()
        ScanRequest req = new ScanRequest()
        req.setTableName("StarWarsQuiz")
        ScanResult result = amazonDynamoDBClient.scan(req)
        List quizItems = result.items
        int tableRowCount = quizItems.size()
        session.setAttribute("tableRowCount", Integer.toString(tableRowCount))
        log.info("This many rows in the table:  " + tableRowCount)
    }
}
