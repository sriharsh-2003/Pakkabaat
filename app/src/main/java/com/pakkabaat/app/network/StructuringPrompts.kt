package com.pakkabaat.app.network

object StructuringPrompts {

    /**
     * This is the prompt from spec section 10.1, verbatim in spirit, with one small
     * technical addition: we ask the model to also emit a JSON block after the
     * human-readable document so the app can reliably populate structured DB fields
     * (amount, agreement type, term list) without fragile text-scraping. The field
     * LABELS in the human-readable document stay in English so both the app and a
     * human glancing at the raw text can line them up; the actual VALUES are written
     * in {{language}}, exactly as section 10.1 intends.
     */
    fun buildSystemPrompt(): String = """
        You are a neutral scribe creating a factual record of a verbal agreement
        between two people in India. You are not a lawyer and you do not give
        legal advice or state who is right.

        You will receive a transcript in the conversation's language, which may mix
        in English words or other local terms. Extract ONLY what was actually said.
        Never infer, estimate, or fill in an amount, date, or condition that was not
        clearly stated — write "unclear — please confirm" instead.

        Do not add opinions. Do not add legal interpretation. Do not invent details.
        If the two parties said conflicting things, record both versions under
        "unclear or disputed" rather than picking one.
    """.trimIndent()

    fun buildUserPrompt(
        transcript: String,
        language: String,
        languageName: String,
        partyAName: String,
        partyBName: String
    ): String = """
        Transcript language: $languageName ($language)
        The two parties are already known — use these exact names, do not guess or
        invent names from the transcript, and do not rename them even if the transcript
        uses a nickname, title, or no name at all:
        Party A = "$partyAName"
        Party B = "$partyBName"

        Transcript:
        ---
        $transcript
        ---

        Reply with exactly two parts, in this order:

        PART 1 — the human-readable record. Keep the field labels below in English
        exactly as written; write the VALUES after each label in $languageName, except
        the two name fields, which must be copied exactly as given above.

        Date of conversation: ...
        Party A (name as stated): $partyAName
        Party B (name as stated): $partyBName
        Type of agreement: <loan / rent / wage / sale / other>
        Key terms: <short bullet list, plain language>
        Amount(s) mentioned: <exact figures only, or "not specified">
        Conditions or deadlines mentioned: ...
        Anything unclear or disputed during the conversation: ...

        PART 2 — after a line containing only ###JSON###, a single JSON object
        (no markdown fences) with this exact shape, values in English/numeric
        where noted, for the app's own bookkeeping only (never shown to the user
        verbatim):

        {
          "agreementType": "loan|rent|wage|sale|other",
          "amount": <number or null>,
          "currency": "INR",
          "dateOfConversationIso": "<YYYY-MM-DD or null>",
          "terms": ["...", "..."],
          "unclearItems": ["...", "..."]
        }
    """.trimIndent()
}
