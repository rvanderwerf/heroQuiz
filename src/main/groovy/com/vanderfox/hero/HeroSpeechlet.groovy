package com.vanderfox.hero

import com.amazon.speech.slu.Intent
import com.amazon.speech.slu.Slot
import com.amazon.speech.speechlet.Device
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.Speechlet
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.speechlet.SupportedInterfaces
import com.amazon.speech.speechlet.interfaces.display.directive.RenderTemplateDirective
import com.amazon.speech.speechlet.interfaces.display.element.ImageInstance
import com.amazon.speech.speechlet.interfaces.display.element.PlainText
import com.amazon.speech.speechlet.interfaces.display.element.RichText
import com.amazon.speech.speechlet.interfaces.display.element.TextField
import com.amazon.speech.speechlet.interfaces.display.element.TripleTextContent
import com.amazon.speech.speechlet.interfaces.display.template.BodyTemplate1
import com.amazon.speech.speechlet.interfaces.display.template.BodyTemplate3
import com.amazon.speech.ui.Card
import com.amazon.speech.ui.Image
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import com.amazon.speech.ui.SimpleCard
import com.amazon.speech.ui.SsmlOutputSpeech
import com.amazon.speech.ui.StandardCard
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
public class HeroSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(HeroSpeechlet.class)

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        LinkedHashMap<String, Question> askedQuestions = new LinkedHashMap()
        session.setAttribute("askedQuestions", askedQuestions)
        session.setAttribute("questionCounter", 10)
        session.setAttribute("score", 0)
        initializeComponents(session)
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        getWelcomeResponse(session)
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getWelcomeResponse(Session session) {
        String speechText = "Welcome to Hero Quiz.  I'm going to ask you 10 questions to test your comic book knowledge.  Say <b>repeat question</b> at any time if you need to hear a question again, or say <b>help</b> if you need some help.  Let's get started:  Oh, wait.  Before I get started, I'm going to say a bunch of stuff to see if Lee got the scrolling screen of text working.  It might have worked and it might not have.  You can be the judge.  All you have to do is just fucking look at me while I am talking and see if the screen is scrolling with the text like it does for other skills native to the Echo Show, like playing music.  <br/>"
        speechText = getQuestion(session, speechText)
        askResponse(speechText, speechText)
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())

        Device device = Device.newInstance()
        device.supportedInterfaces

        Intent intent = request.getIntent()
        String intentName = (intent != null) ? intent.getName() : null
        log.debug("Intent = " + intentName)
        switch (intentName) {
            case "AnswerIntent":
                getAnswer(intent.getSlot("Answer"), session)
                break
            case "DontKnowIntent":
                processAnswer(session, 5)
                break
            case "AMAZON.HelpIntent":
                getHelpResponse(session)
                break
            case "AMAZON.CancelIntent":
                sayGoodbye()
                break
            case "AMAZON.RepeatIntent":
                repeatQuestion(session)
                break
            case "AMAZON.StopIntent":
                sayGoodbye()
                break
            default:
                didNotUnderstand()
                break
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse sayGoodbye() {
        String speechText = "OK.  I'm going to stop the game now."
        tellResponse(speechText, speechText)
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private String getQuestion(final Session session, String speechText) {
        Question question = getRandomUnaskedQuestion(session)
        session.setAttribute("lastQuestionAsked", question)

        speechText += "\n"
        speechText += question.getQuestion() + "<br/><br/>"
        String[] options = question.getOptions()
        int index = 1
        for(String option: options) {
            speechText += (index++) + "\n\n\n" + option + "<br/>"
        }
        speechText
    }

    private Question getRandomUnaskedQuestion(Session session) {
        LinkedHashMap<String, Question> askedQuestions = (LinkedHashMap) session.getAttribute("askedQuestions")
        Question question = getRandomQuestion(session)
        while(askedQuestions.get(question.getQuestion()) != null) {
            question = getRandomQuestion(session)
        }
        askedQuestions.put(question.getQuestion(), question)
        session.setAttribute("askedQuestions", askedQuestions)
        question
    }

    private Question getRandomQuestion(Session session) {
        int tableRowCount = Integer.parseInt((String) session.getAttribute("tableRowCount"))
        int questionIndex = (new Random().nextInt() % tableRowCount).abs()
        log.info("The question index is:  " + questionIndex)
        Question question = getQuestion(questionIndex)
        question
    }

    private Question getQuestion(int questionIndex) {


        DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient())
        Table table = dynamoDB.getTable("HeroQuiz")
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
        question.setAnswer(questionAnswer - 1)
        question.setIndex(questionIndex)
        log.info("question retrieved:  " + question.getIndex())
        log.info("question retrieved:  " + question.getQuestion())
        log.info("question retrieved:  " + question.getAnswer())
        log.info("question retrieved:  " + question.getOptions().length)
        question
    }

    private SpeechletResponse askResponse(String cardText, String speechText) {

        BodyTemplate1 template = new BodyTemplate1()
        template.setTitle("Hero Quiz")
        BodyTemplate1.TextContent textContent = new BodyTemplate1.TextContent()
        RichText richText = new RichText()
        richText.text = cardText
        textContent.setPrimaryText(richText)
        template.setTextContent(textContent)
        com.amazon.speech.speechlet.interfaces.display.element.Image backgroundImage = new com.amazon.speech.speechlet.interfaces.display.element.Image()
        ImageInstance imageInstance = new ImageInstance()
        imageInstance.setUrl("https://s-media-cache-ak0.pinimg.com/originals/cc/a8/51/cca8515138697c4027df4cf439b83bb5.jpg")
        ArrayList<ImageInstance> imageInstances = new ArrayList()
        imageInstances.add(imageInstance)
        backgroundImage.setSources(imageInstances)
        template.setBackgroundImage(backgroundImage)
        RenderTemplateDirective renderTemplateDirective = new RenderTemplateDirective()
        renderTemplateDirective.setTemplate(template)

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse response = new SpeechletResponse()
        ArrayList directives = new ArrayList()
        directives.add(renderTemplateDirective)
        response.setDirectives(directives)

        response.setNullableShouldEndSession(false)
        response.setOutputSpeech(speech)
        response.setReprompt(reprompt)
        response

    }

    private SpeechletResponse tellResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard()
        card.setTitle("Hero Quiz")
        card.setContent(cardText)

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        // Create reprompt
        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse.newTellResponse(speech, card)
    }

    private SpeechletResponse askResponseFancy(String cardText, String speechText, String fileUrl) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard()
        card.setTitle("Hero Quiz")
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
    private SpeechletResponse repeatQuestion(final Session session) {
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        String speechText = ""

        speechText += "\n"
        speechText += question.getQuestion() + "\n"
        String[] options = question.getOptions()
        int index = 1
        for(String option: options) {
            speechText += (index++) + "\n\n\n\n" + option + "\n\n\n"
        }
        askResponse(speechText, speechText)

    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getAnswer(Slot query, final Session session) {

        def speechText

        int guessedAnswer = Integer.parseInt(query.getValue()) - 1
        log.info("Guessed answer is:  " + query.getValue())

        return processAnswer(session, guessedAnswer)
    }

    private SpeechletResponse processAnswer(Session session, int guessedAnswer) {
        def speechText
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        def answer = question.getAnswer()
        log.info("correct answer is:  " + answer)
        int questionCounter = Integer.parseInt((String) session.getAttribute("questionCounter"))

        questionCounter = decrementQuestionCounter(session)

        if (guessedAnswer == answer) {
            speechText = "You got it right."
            int score = (Integer) session.getAttribute("score")
            score++
            session.setAttribute("score", score)
            questionMetricsCorrect(question.getIndex())
        } else {
            speechText = "You got it wrong."
            questionMetricsWrong(question.getIndex())
        }

        log.info("questionCounter:  " + questionCounter)

        if (questionCounter > 0) {
            session.setAttribute("state", "askQuestion")
            speechText = getQuestion(session, speechText)
            return askResponse(speechText, speechText)
        } else {
            int score = (Integer) session.getAttribute("score")
            speechText += "\n\nYou answered ${score} questions correctly."
            userMetrics(session.getUser().userId, score)
            return tellResponse(speechText, speechText)
        }
    }

    private void questionMetricsCorrect(int questionIndex) {
        questionMetrics(questionIndex, true)
    }

    private void questionMetricsWrong(int questionIndex) {
        questionMetrics(questionIndex, false)
    }

    private void questionMetrics(int questionIndex, boolean correct) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient())
        Table table = dynamoDB.getTable("HeroQuizMetrics")
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

    private void userMetrics(String userId, int score) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient())
        Table table = dynamoDB.getTable("HeroQuizUserMetrics")
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
    private SpeechletResponse getHelpResponse(Session session) {
        String speechText = ""
        speechText = "You can say stop or cancel to end the game at any time.  If you need a question repeated, say repeat question."
        askResponse(speechText, speechText)
    }

    private SpeechletResponse didNotUnderstand() {
        String speechText = "I'm sorry.  I didn't understand what you said.  Say help me for help."
        askResponse(speechText, speechText)
    }

    private int decrementQuestionCounter(Session session) {
        int questionCounter = (int) session.getAttribute("questionCounter")
        questionCounter--
        session.setAttribute("questionCounter", questionCounter)
        questionCounter

    }

    /**
     * Initializes the instance components if needed.
     */
    private void initializeComponents(Session session) {
        AmazonDynamoDBClient amazonDynamoDBClient
        amazonDynamoDBClient = new AmazonDynamoDBClient()
        ScanRequest req = new ScanRequest()
        req.setTableName("HeroQuiz")
        ScanResult result = amazonDynamoDBClient.scan(req)
        List quizItems = result.items
        int tableRowCount = quizItems.size()
        session.setAttribute("tableRowCount", Integer.toString(tableRowCount))
        log.info("This many rows in the table:  " + tableRowCount)
    }
}
