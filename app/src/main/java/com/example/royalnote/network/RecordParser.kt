package com.example.royalnote.network

interface RecordParser {
    suspend fun parseRecords(text: String): ParsedRecords
}
