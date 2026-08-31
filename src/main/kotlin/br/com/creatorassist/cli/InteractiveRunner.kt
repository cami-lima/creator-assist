package br.com.creatorassist.cli

import br.com.creatorassist.agents.IncomeClassifierAgent
import br.com.creatorassist.agents.Orchestrator
import br.com.creatorassist.report.ReportFormatter
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File

/**
 * A minimal, real, interactive entry point: the user types their
 * income in free text and optionally points to a real contract file
 * on their own machine, and gets back the same polished report a real
 * creator would see (see ReportFormatter). This is deliberately
 * separate from the evaluation harness (EvalRunner /
 * SampleReportGenerator), which only ever reads bundled test
 * fixtures. This is the first genuinely user-driven input path in
 * the project, addressing a real gap: until this was added, there was
 * no way for an actual person to type anything into this system at
 * all, only pre-packaged synthetic test data.
 *
 * Run with: ./gradlew bootRun --args='--spring.profiles.active=interactive'
 *
 * Because it shares the same file-based H2 database as the default
 * profile, running this twice with the same name demonstrates memory
 * persistence directly: the second run remembers whatever tax regime
 * and accumulated revenue the first run stored.
 */
@Component
@Profile("interactive")
class InteractiveRunner(
    private val incomeClassifier: IncomeClassifierAgent,
    private val orchestrator: Orchestrator
) : CommandLineRunner {

    override fun run(vararg args: String?) = runBlocking {
        try {
            runInteraction()
        } catch (e: Exception) {
            println()
            println("Ocorreu um erro ao processar (provavelmente a chamada a API falhou, ex: limite de cota).")
            println("Detalhe tecnico: ${e.message}")
            println("Espere um minuto e tente rodar de novo.")
        }
    }

    private suspend fun runInteraction() {
        println("=== Creator Assist - assistente fiscal e contratual ===")
        println()

        print("Seu nome ou apelido (usado para lembrar seus dados entre execucoes): ")
        val creatorName = readLine()?.trim().takeUnless { it.isNullOrBlank() } ?: "criador-anonimo"

        print("Mes de referencia (ex: Junho de 2026): ")
        val referenceMonth = readLine()?.trim().takeUnless { it.isNullOrBlank() } ?: "mes nao informado"

        println()
        println("Descreva os recebimentos deste mes (marcas, valores, moedas, datas).")
        println("Pode escrever em texto livre, do seu jeito. Digite FIM numa linha sozinha quando terminar:")
        val incomeText = readMultilineUntilSentinel()

        if (incomeText.isBlank()) {
            println("Nenhum recebimento informado. Encerrando.")
            return
        }

        val classification = incomeClassifier.classifyFreeText(incomeText)

        if (classification.ambiguous) {
            println()
            println("Nao consegui interpretar os recebimentos com seguranca:")
            println(classification.messageToUser ?: "Informacao insuficiente.")
            println()
            println("Rode o programa de novo com valores mais exatos.")
            return
        }

        println()
        print("Caminho do arquivo do contrato de patrocinio (ou pressione Enter para pular): ")
        val contractPath = readLine()?.trim()
        val contractText = if (contractPath.isNullOrBlank()) {
            null
        } else {
            val file = File(contractPath)
            if (!file.exists()) {
                println("Arquivo nao encontrado em '$contractPath' - seguindo sem analise de contrato.")
                null
            } else {
                file.readText()
            }
        }

        println()
        println("Processando...")
        println()

        val finalReport = orchestrator.process(
            creatorId = creatorName,
            referenceMonth = referenceMonth,
            incomeEntries = classification.incomeEntries,
            contractText = contractText
        )

        val formatted = ReportFormatter.format(
            creatorName = creatorName,
            referenceMonth = referenceMonth,
            report = finalReport
        )

        println(formatted)

        val outputFile = File("relatorio_${creatorName.replace(" ", "_")}.md")
        outputFile.writeText(formatted)
        println("Relatorio tambem salvo em: ${outputFile.absolutePath}")
    }

    private fun readMultilineUntilSentinel(): String {
        val lines = mutableListOf<String>()
        while (true) {
            val line = readLine() ?: break
            if (line.trim().equals("FIM", ignoreCase = true)) break
            lines.add(line)
        }
        return lines.joinToString("\n")
    }
}