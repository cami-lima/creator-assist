package br.com.creatorassist

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CreatorAssistApplication

fun main(args: Array<String>) {
    runApplication<CreatorAssistApplication>(*args)
}
