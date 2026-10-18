package app.chalkroom.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.chalkroom.feature.choosestudent.ChooseStudentScreen
import app.chalkroom.feature.closing.ClosingScreen
import app.chalkroom.feature.classroom.ClassroomScreen
import app.chalkroom.feature.lesson.LessonScreen
import app.chalkroom.feature.pastenotes.PasteNotesScreen
import app.chalkroom.feature.reading.ReadingScreen
import app.chalkroom.ui.theme.Chalkroom

@Composable
fun ChalkroomNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Classroom,
        modifier = Modifier.fillMaxSize().background(Chalkroom.colors.paper),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable<Classroom> {
            ClassroomScreen(
                onTeachSomething = { nav.navigate(PasteNotes()) },
                onResumeLesson = { nav.navigate(Lesson(it)) },
            )
        }
        composable<PasteNotes> { entry ->
            val route = entry.toRoute<PasteNotes>()
            PasteNotesScreen(
                preselected = route.preselected,
                onChooseStudent = { nav.navigate(ChooseStudent) },
                onRead = { nav.navigate(Reading(it)) },
            )
        }
        composable<ChooseStudent> {
            ChooseStudentScreen(
                onRead = { nav.navigate(Reading(it)) },
                onResumeLesson = { nav.navigate(Lesson(it)) { popUpTo(Classroom) } },
            )
        }
        composable<Reading> { entry ->
            val route = entry.toRoute<Reading>()
            ReadingScreen(
                studentId = route.studentId,
                onLesson = { nav.navigate(Lesson(route.studentId)) { popUpTo(Classroom) } },
                onBackToNotes = { if (!nav.popBackStack<PasteNotes>(inclusive = false)) nav.navigate(PasteNotes()) { popUpTo(Classroom) } },
            )
        }
        composable<Lesson> { entry ->
            val studentId = entry.toRoute<Lesson>().studentId
            LessonScreen(
                studentId = studentId,
                onClosing = { nav.navigate(Closing(studentId)) { popUpTo(Classroom) } },
            )
        }
        composable<Closing> { entry ->
            ClosingScreen(
                studentId = entry.toRoute<Closing>().studentId,
                onTeachAgain = { nav.navigate(PasteNotes(preselected = it)) { popUpTo(Classroom) } },
                onBackToClass = { nav.popBackStack(Classroom, inclusive = false) },
            )
        }
    }
}
