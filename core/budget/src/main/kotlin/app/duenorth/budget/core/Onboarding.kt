package app.duenorth.budget.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The first-run page the person is on (spec 011 data-model.md). */
@Serializable
enum class OnboardingStep {
    WELCOME,
    ADDRESS,
    SIGN_IN,
    FILES,
    CREATE,
}

@Serializable
enum class OnboardingPath {
    SERVER,
    PHONE,
}

/** Where `back` from the new-budget page returns. */
@Serializable
enum class CreateReturn {
    WELCOME,
    FILES,
}

/** Where the server path started: the first-run welcome page, or "add" in settings (spec 012). */
@Serializable
enum class OnboardingOrigin {
    FIRST_RUN,
    SETTINGS,
}

/**
 * First-run progress, kept in `onboarding.json` so leaving the app resumes the same step.
 * It never holds a password, a token, or a budget key.
 */
@Serializable
data class OnboardingProgress(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val path: OnboardingPath? = null,
    val addressText: String = "",
    /** The address that passed the check, with its sign-ins; null until one has. */
    val readyAddress: String? = null,
    val methods: Set<SignInMethod> = emptySet(),
    val instanceLabel: String? = null,
    val openIdPendingAddress: String? = null,
    val createName: String = "",
    val createCurrency: String? = null,
    val createReturn: CreateReturn = CreateReturn.WELCOME,
    val origin: OnboardingOrigin = OnboardingOrigin.FIRST_RUN,
)

class OnboardingStore(
    private val file: File,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun read(): OnboardingProgress? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(OnboardingProgress.serializer(), file.readText()) }.getOrNull()
    }

    @Synchronized
    fun write(progress: OnboardingProgress) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(OnboardingProgress.serializer(), progress))
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized
    fun clear() {
        file.delete()
    }
}
