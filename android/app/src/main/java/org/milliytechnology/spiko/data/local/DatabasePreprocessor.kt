package org.milliytechnology.spiko.data.local

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.milliytechnology.spiko.data.remote.models.FeedbackBreakdown
import org.milliytechnology.spiko.data.remote.models.TranscriptEntry
import java.util.UUID

/**
 * A RoomDatabase.Callback to pre-populate the database with sample exam results
 * the first time it is created.
 */
class DatabasePreprocessor(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Launch a coroutine to perform the database operations off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val examResultDao = database.multilevelExamResultDao()

            // Insert all the sample data.
            val allResults = listOf(
                SampleExamData.result1_full_low_score,
                SampleExamData.result2_part2_practice,
                SampleExamData.result3_full_medium_score,
                SampleExamData.result4_part3_practice,
                SampleExamData.result5_part1_1_practice,
                SampleExamData.result6_englishFeedbackResult, // Original good score
                SampleExamData.result7_uzbekFeedbackResult, // Original good score (Uzbek)
                SampleExamData.result8_full_dip_score // A recent dip to make graph interesting
            )
            allResults.forEach { examResultDao.insert(it) }
        }
    }
}

/**
 * An object holding realistic sample data for the ExamResultEntity.
 * This simulates the JSON output from your AI analysis prompt.
 */
object SampleExamData {
    private val gson = Gson()

    // Define the specific types for Gson deserialization using TypeToken.
    private val feedbackBreakdownListType = object : TypeToken<List<FeedbackBreakdown>>() {}.type
    private val transcriptEntryListType = object : TypeToken<List<TranscriptEntry>>() {}.type

    // --- SHARED TRANSCRIPT (can be reused for multiple entries) ---
    private val sampleTranscript = gson.fromJson<List<TranscriptEntry>>(
        gson.toJson(
            listOf(
                mapOf("speaker" to "Examiner", "text" to "Let's talk about your hometown."),
                mapOf("speaker" to "User", "text" to "My hometown is a small city. It is famous for its historical places. Many tourists come every year."),
                mapOf("speaker" to "Examiner", "text" to "Compare online shopping and traditional shopping."),
                mapOf("speaker" to "User", "text" to "Online shopping is more convenient because you can buy from home. But traditional shopping you can see the product before you buy. I think both are good."),
                mapOf("speaker" to "Examiner", "text" to "Describe a memorable event from your life."),
                mapOf("speaker" to "User", "text" to "A memorable event was my graduation day. I felt very happy and proud. My family was there to celebrate with me."),
                mapOf("speaker" to "Examiner", "text" to "Do you think artificial intelligence is a threat to humanity?"),
                mapOf("speaker" to "User", "text" to "It can be a threat if we are not careful. For example, AI can take jobs from people. On other hand, it can also help us solve big problems like diseases. So we need to control it carefully.")
            )
        ), transcriptEntryListType
    )

    // --- HELPER FUNCTION to create simplified feedback for data variety ---
    private fun createSimpleFeedback(part: String, score: Int, feedbackText: String): List<FeedbackBreakdown> {
        val json = gson.toJson(
            listOf(
                mapOf(
                    "part" to part,
                    "score" to score,
                    "overallFeedback" to feedbackText,
                    "detailedBreakdown" to null // For simplicity, we can nullify this for older data
                )
            )
        )
        return gson.fromJson(json, feedbackBreakdownListType)
    }

    private fun createSimpleFullFeedback(s1: Int, s2: Int, s3: Int, s4: Int): List<FeedbackBreakdown> {
        val json = gson.toJson(
            listOf(
                mapOf("part" to "Part 1.1", "score" to s1, "overallFeedback" to "Basic feedback."),
                mapOf("part" to "Part 1.2", "score" to s2, "overallFeedback" to "Basic feedback."),
                mapOf("part" to "Part 2", "score" to s3, "overallFeedback" to "Basic feedback."),
                mapOf("part" to "Part 3", "score" to s4, "overallFeedback" to "Basic feedback.")
            )
        )
        return gson.fromJson(json, feedbackBreakdownListType)
    }


    // --- NEW SAMPLE DATA POINTS ---

    val result1_full_low_score = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 38,
        feedbackBreakdown = createSimpleFullFeedback(6, 9, 10, 13),
        transcript = sampleTranscript,
        practicedPart = "FULL",
        createdAt = "2025-06-25T11:00:00.000Z" // ~2 months ago
    )

    val result2_part2_practice = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 10,
        feedbackBreakdown = createSimpleFeedback("Part 2", 10, "You spoke clearly but needed to add more details to fill the time."),
        transcript = sampleTranscript.filter { it.speaker == "User" }.take(1),
        practicedPart = "P2",
        createdAt = "2025-07-05T15:20:00.000Z"
    )

    val result3_full_medium_score = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 45,
        feedbackBreakdown = createSimpleFullFeedback(7, 13, 12, 13),
        transcript = sampleTranscript,
        practicedPart = "FULL",
        createdAt = "2025-07-18T09:15:00.000Z"
    )

    val result4_part3_practice = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 13,
        feedbackBreakdown = createSimpleFeedback("Part 3", 13, "Your argument structure is improving. Try to use more formal transition words."),
        transcript = sampleTranscript.filter { it.speaker == "User" },
        practicedPart = "P3",
        createdAt = "2025-08-02T18:00:00.000Z" // Within 1 month
    )

    val result5_part1_1_practice = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 7,
        feedbackBreakdown = createSimpleFeedback("Part 1.1", 7, "Good direct answers. Focus on extending your responses with examples."),
        transcript = sampleTranscript.filter { it.speaker == "User" },
        practicedPart = "P1_1",
        createdAt = "2025-08-10T12:00:00.000Z" // Within 1 month
    )

    val result8_full_dip_score = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 42,
        feedbackBreakdown = createSimpleFullFeedback(7, 11, 11, 13),
        transcript = sampleTranscript,
        practicedPart = "FULL",
        createdAt = "2025-08-24T16:45:00.000Z" // Very recent
    )

    // --- ORIGINAL DETAILED DATA (Renamed for clarity) ---

    private val englishFeedbackJson = gson.toJson(
        listOf(
            mapOf("part" to "Part 1.1", "score" to 8, "overallFeedback" to "You answered the questions directly and clearly. Try to use more complex sentences and expand your answers a bit more.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "Your speech was generally clear and easy to follow.","suggestion" to "Avoid short, simple sentences. Try connecting your ideas with words like 'although', 'however', or 'in addition'.","example" to "You said: 'I like pop music. Taylor Swift is my favorite singer her songs are very good.' A better way to say this is: 'I primarily enjoy pop music, and my favorite artist is Taylor Swift because her songs are very well-written and catchy.'"),"lexicalResource" to mapOf("positive" to "You used appropriate vocabulary for the topics.","suggestion" to "Instead of 'good' or 'very unique', try more descriptive words like 'captivating', 'outstanding', or 'distinctive'.","example" to "You said: 'the atmosphere is... uh... very unique.' A better way to say this is: 'the atmosphere is truly distinctive and memorable.'"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "You correctly used simple present and past tenses.","suggestion" to "Remember to use articles like 'a', 'an', and 'the' correctly. For example, 'it helps me relax'.","example" to "You said: 'it help me relax'. A better way to say this is: 'it helps me relax'."),"taskAchievement" to mapOf("positive" to "You directly answered all three questions.","suggestion" to "You can provide a little more detail or a brief example to make your answers more complete.","example" to "For the question about your hobby, you could add: 'For instance, last week I finished a fascinating book about ancient history.'"))),
            mapOf("part" to "Part 1.2", "score" to 15, "overallFeedback" to "Good job comparing the two pictures and answering the follow-up questions. Your main points were clear.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "You effectively used comparison words like 'but' to contrast the two images.","suggestion" to "Try to structure your initial comparison more formally, for example, by starting with 'The most obvious difference is...'.","example" to "You said: 'In first picture there is a city... In second picture it is a village'. A better way to say this is: 'The first picture depicts a bustling city, whereas the second one shows a peaceful village scene.'"),"lexicalResource" to mapOf("positive" to "You used good contrasting words like 'busy' and 'peaceful'.","suggestion" to "Expand your vocabulary for describing places, using words like 'urban', 'rural', 'serene', or 'congested'.","example" to "You said: 'village quiet and... green'. A better way to say this is: 'the village appears serene and is surrounded by lush greenery.'"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "You formed your comparisons correctly.","suggestion" to "Pay attention to subject-verb agreement, for instance, 'a big city has' instead of 'big city has'.","example" to "You said: 'Big city has more opportunities'. A better way to say this is: 'A big city has more opportunities...'"),"taskAchievement" to mapOf("positive" to "You successfully identified key differences and answered the questions.","suggestion" to "No major issues here. You fulfilled the task requirements well.","example" to "N/A"))),
            mapOf("part" to "Part 2", "score" to 12, "overallFeedback" to "You described the picture well and addressed the key points. Your speech was coherent, but could be more detailed.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "You spoke for the required time without long pauses.","suggestion" to "Use linking words to connect your ideas more smoothly, such as 'Furthermore,' or 'As a result,'.","example" to "You said: '...spend time together for making good memories and stronger relationship.' A better way to say this is: '...spend time together, which is crucial for making good memories and, as a result, building a stronger relationship.'"),"lexicalResource" to mapOf("positive" to "You used relevant vocabulary like 'picnic', 'sunny', and 'relationship'.","suggestion" to "Try to add more descriptive adjectives. For example, 'a loving family' or 'a delicious picnic'.","example" to "N/A"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "Your sentence structure was clear and easy to understand.","suggestion" to "Be careful with plural nouns, for instance, 'stronger relationships'.","example" to "You said: '...stronger relationship'. A better way to say this is: '...building stronger relationships.'"),"taskAchievement" to mapOf("positive" to "You successfully described the scene and explained the importance of family time.","suggestion" to "To improve, you could have speculated a bit more, for example, 'Perhaps they are celebrating a special occasion.'","example" to "N/A"))),
            mapOf("part" to "Part 3", "score" to 14, "overallFeedback" to "You presented a balanced argument with clear points for and against the topic. This was a strong performance.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "You structured your argument logically, presenting the 'for' points first, followed by the 'against' points.","suggestion" to "Use transition phrases to signal the shift in your argument, such as 'On the other hand,' or 'However,'.","example" to "You said: 'But against technology...'. A better way to say this is: 'On the other hand, technology in the classroom can also be a distraction.'"),"lexicalResource" to mapOf("positive" to "You used relevant terms like 'distraction', 'apps', and 'fair'.","suggestion" to "Try to use more formal vocabulary for arguments, such as 'benefits', 'drawbacks', 'accessible', or 'equitable'.","example" to "You said: '...so it is not fair for them'. A better way to say this is: '...so the access to education is not equitable for them.'"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "Your grammar was mostly accurate throughout your monologue.","suggestion" to "A small point: use 'studying' after 'instead of' (gerund form).","example" to "You said: '...instead of study'. A better way to say this is: '...instead of studying.'"),"taskAchievement" to mapOf("positive" to "You effectively used points from both sides to create a balanced argument as the task required.","suggestion" to "Excellent work. You fully achieved the objective of this task.","example" to "N/A")))
        )
    )

    private val uzbekFeedbackJson = gson.toJson(
        listOf(
            mapOf("part" to "Part 1.1", "score" to 8, "overallFeedback" to "Siz savollarga to'g'ridan-to'g'ri va aniq javob berdingiz. Murakkabroq gaplardan foydalanishga va javoblaringizni biroz kengaytirishga harakat qiling.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "Nutqingiz umuman olganda ravon va tushunarli edi.","suggestion" to "Qisqa, oddiy gaplardan saqlaning. Fikrlaringizni 'although', 'however' yoki 'in addition' kabi bog'lovchilar bilan bog'lashga harakat qiling.","example" to "Siz aytdingiz: 'I like pop music. Taylor Swift is my favorite singer her songs are very good.' Buni aytishning yaxshiroq usuli: 'Men asosan pop musiqasini yoqtiraman va mening sevimli san'atkorim Taylor Swift, chunki uning qo'shiqlari juda yaxshi yozilgan va o'ziga tortadigan.'"),"lexicalResource" to mapOf("positive" to "Mavzular uchun mos lug'atdan foydalandingiz.","suggestion" to "'Good' yoki 'very unique' o'rniga 'captivating' (maftunkor), 'outstanding' (ajoyib) yoki 'distinctive' (o'ziga xos) kabi ta'riflovchi so'zlarni sinab ko'ring.","example" to "Siz aytdingiz: 'the atmosphere is... uh... very unique.' Buni aytishning yaxshiroq usuli: 'atmosfera haqiqatan ham o'ziga xos va esda qolarli.'"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "Siz oddiy hozirgi va o'tgan zamonlardan to'g'ri foydalandingiz.","suggestion" to "'a', 'an' va 'the' kabi artikllardan to'g'ri foydalanishni unutmang. Masalan, 'it helps me relax'.","example" to "Siz aytdingiz: 'it help me relax'. Buni aytishning yaxshiroq usuli: 'it helps me relax'."),"taskAchievement" to mapOf("positive" to "Siz uchala savolga ham to'g'ridan-to'g'ri javob berdingiz.","suggestion" to "Javoblaringizni yanada to'liqroq qilish uchun biroz ko'proq tafsilot yoki qisqa misol keltirishingiz mumkin.","example" to "Xobbingiz haqidagi savolga qo'shimcha qilishingiz mumkin edi: 'Masalan, o'tgan hafta men qadimgi tarix haqida ajoyib bir kitobni tugatdim.'"))),
            mapOf("part" to "Part 1.2", "score" to 15, "overallFeedback" to "Ikki rasmni solishtirib, keyingi savollarga javob berganingiz uchun rahmat. Asosiy fikrlaringiz aniq edi.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "Ikki rasmni taqqoslash uchun 'but' kabi solishtirish so'zlarini samarali ishlatdingiz.","suggestion" to "Dastlabki taqqoslashingizni rasmiyroq tuzishga harakat qiling, masalan, 'Eng yaqqol farq shundaki...' deb boshlang.","example" to "Siz aytdingiz: 'In first picture there is a city... In second picture it is a village'. Buni aytishning yaxshiroq usuli: 'Birinchi rasmda gavjum shahar tasvirlangan, ikkinchisida esa tinch qishloq manzarasi ko'rsatilgan.'"),"lexicalResource" to mapOf("positive" to "'Busy' va 'peaceful' kabi yaxshi qarama-qarshi so'zlarni ishlatdingiz.","suggestion" to "Joylarni tavsiflash uchun lug'atingizni kengaytiring, 'urban' (shaharga oid), 'rural' (qishloqqa oid), 'serene' (osoyishta) yoki 'congested' (tiqilinch) kabi so'zlardan foydalaning.","example" to "Siz aytdingiz: 'village quiet and... green'. Buni aytishning yaxshiroq usuli: 'qishloq osoyishta ko'rinadi va yam-yashil o'simliklar bilan o'ralgan.'"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "Siz taqqoslashlaringizni to'g'ri shakllantirdingiz.","suggestion" to "Ega va kesim moslashuviga e'tibor bering, masalan, 'big city has' o'rniga 'a big city has'.","example" to "Siz aytdingiz: 'Big city has more opportunities'. Buni aytishning yaxshiroq usuli: 'A big city has more opportunities...'"),"taskAchievement" to mapOf("positive" to "Siz asosiy farqlarni muvaffaqiyatli aniqladingiz va savollarga javob berdingiz.","suggestion" to "Bu yerda jiddiy muammolar yo'q. Siz vazifa talablarini yaxshi bajardingiz.","example" to "Mavjud emas"))),
            mapOf("part" to "Part 2", "score" to 12, "overallFeedback" to "Rasmni yaxshi tasvirlab berdingiz va asosiy fikrlarga to'xtaldingiz. Nutqingiz izchil edi, lekin batafsilroq bo'lishi mumkin edi.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "Siz talab qilingan vaqt davomida uzoq pauzalarsiz gapirdingiz.","suggestion" to "Fikrlaringizni yanada silliqroq bog'lash uchun 'Furthermore,' (Bundan tashqari) yoki 'As a result,' (Natijada) kabi bog'lovchi so'zlardan foydalaning.","example" to "Siz aytdingiz: '...spend time together for making good memories and stronger relationship.' Buni aytishning yaxshiroq usuli: '...birga vaqt o'tkazish, bu yaxshi xotiralar uchun muhim va natijada mustahkamroq munosabatlarni quradi.'"),"lexicalResource" to mapOf("positive" to "'Picnic', 'sunny' va 'relationship' kabi tegishli so'zlardan foydalandingiz.","suggestion" to "Ko'proq sifatlar qo'shishga harakat qiling. Masalan, 'a loving family' (mehribon oila) yoki 'a delicious picnic' (mazali piknik).","example" to "Mavjud emas"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "Gap tuzilishingiz aniq va tushunarli edi.","suggestion" to "Ko'plikdagi otlarga ehtiyot bo'ling, masalan, 'stronger relationships'.","example" to "Siz aytdingiz: '...stronger relationship'. Buni aytishning yaxshiroq usuli: '...mustahkamroq munosabatlar qurish.'"),"taskAchievement" to mapOf("positive" to "Siz manzarani muvaffaqiyatli tasvirladingiz va oilaviy vaqtning muhimligini tushuntirdingiz.","suggestion" to "Yaxshilash uchun biroz ko'proq taxmin qilishingiz mumkin edi, masalan, 'Balki ular maxsus bir voqeani nishonlayotgandir.'","example" to "Mavjud emas"))),
            mapOf("part" to "Part 3", "score" to 14, "overallFeedback" to "Siz mavzu bo'yicha aniq ijobiy va salbiy fikrlar bilan muvozanatli argument taqdim etdingiz. Bu kuchli chiqish bo'ldi.", "detailedBreakdown" to mapOf("fluencyAndCoherence" to mapOf("positive" to "Argumentingizni mantiqiy tuzdingiz, avval 'ijobiy' keyin esa 'salbiy' fikrlarni taqdim etdingiz.","suggestion" to "Argumentingizdagi o'zgarishni bildirish uchun 'On the other hand,' (Boshqa tomondan) yoki 'However,' (Biroq) kabi o'tish iboralaridan foydalaning.","example" to "Siz aytdingiz: 'But against technology...'. Buni aytishning yaxshiroq usuli: 'Boshqa tomondan, sinfdagi texnologiya chalg'ituvchi bo'lishi ham mumkin.'"),"lexicalResource" to mapOf("positive" to "'Distraction', 'apps' va 'fair' kabi tegishli atamalardan foydalandingiz.","suggestion" to "Argumentlar uchun 'benefits' (foydalari), 'drawbacks' (kamchiliklari), 'accessible' (mavjud) yoki 'equitable' (adolatli) kabi rasmiyroq lug'atdan foydalanishga harakat qiling.","example" to "Siz aytdingiz: '...so it is not fair for them'. Buni aytishning yaxshiroq usuli: '...shuning uchun ta'lim olish imkoniyati ular uchun teng (adolatli) emas.'"),"grammaticalRangeAndAccuracy" to mapOf("positive" to "Monologingiz davomida grammatikangiz asosan to'g'ri edi.","suggestion" to "Kichik bir eslatma: 'instead of' dan keyin 'studying' (gerund shakli) dan foydalaning.","example" to "Siz aytdingiz: '...instead of study'. Buni aytishning yaxshiroq usuli: '...o'qish o'rniga.'"),"taskAchievement" to mapOf("positive" to "Vazifa talab qilganidek, muvozanatli argument yaratish uchun har ikki tomonning fikrlaridan samarali foydalandingiz.","suggestion" to "Ajoyib ish. Siz bu vazifaning maqsadiga to'liq erishdingiz.","example" to "Mavjud emas")))
        )
    )

    val result6_englishFeedbackResult = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 49,
        feedbackBreakdown = gson.fromJson(englishFeedbackJson, feedbackBreakdownListType),
        transcript = sampleTranscript,
        practicedPart = "FULL",
        createdAt = "2025-08-20T10:30:00.000Z" // Within 1 month
    )

    val result7_uzbekFeedbackResult = ExamResultEntity(
        id = UUID.randomUUID().toString(),
        userId = "sample_user_01",
        totalScore = 49,
        feedbackBreakdown = gson.fromJson(uzbekFeedbackJson, feedbackBreakdownListType),
        transcript = sampleTranscript,
        practicedPart = "FULL",
        createdAt = "2025-08-22T14:00:00.000Z" // Within 1 week
    )
}