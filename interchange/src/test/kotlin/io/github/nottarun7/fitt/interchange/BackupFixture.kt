package io.github.nottarun7.fitt.interchange

internal fun backupFixture(): BackupDocument {
    val routineExercise = BackupExercise("rx-1", "=SUM(1,2)\n\"शक्ति\" 🏋️", 2, BackupMetric.SECONDS, 3, 20, "l-sit", true, true)
    val logged = BackupLoggedExercise(routineExercise.copy(id = "wx-1", name = "L-sit snapshot"), listOf(
        BackupSet("set-1", 1, 20, null, WeightUnit.KG, false),
        BackupSet("set-2", 4, 15, 0.0, WeightUnit.LB, false),
        BackupSet("set-3", 9, 12, 42.5, WeightUnit.LB, true),
    ), null)
    return BackupDocument(9000,
        listOf(BackupRoutine("r-1", "Upper, body \"A\"", 100, listOf(routineExercise)), BackupRoutine("r-empty", "Empty draft", 50, emptyList())),
        listOf(BackupWorkout("w-done", "Finished snapshot", "deleted-routine", 1000, 2000, listOf(logged)),
            BackupWorkout("w-active", "Active session", "r-1", 3000, null, listOf(logged.copy(
                exercise = logged.exercise.copy(id = "wx-active", metric = BackupMetric.REPS),
                sets = emptyList(), draft = BackupDraft("not valid yet", "12.", WeightUnit.LB, true)))),
            BackupWorkout("w-empty", "Empty legacy history", null, 500, 600, emptyList())),
    )
}
