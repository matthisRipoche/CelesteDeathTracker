/*
 * Quiz réutilisable : un <div class="quiz" data-correct="b"> contient un <fieldset> de boutons radio
 * (value = a, b, c, d), un <p class="feedback" aria-live="polite"> et un <template class="why"> avec
 * l'explication. Retour immédiat au clic. Un <p data-quiz-score> affiche le total à la fin.
 * Aucune donnée n'est stockée.
 */
(() => {
  const quizzes = [...document.querySelectorAll('.quiz')];
  const score = document.querySelector('[data-quiz-score]');
  const done = new Set();
  const right = new Set();

  quizzes.forEach((quiz, i) => {
    const feedback = quiz.querySelector('.feedback');
    quiz.addEventListener('change', (event) => {
      const input = event.target;
      if (!(input instanceof HTMLInputElement)) return;
      const ok = input.value === quiz.dataset.correct;

      quiz.querySelectorAll('label').forEach((l) => l.classList.remove('ok', 'ko'));
      input.closest('label').classList.add(ok ? 'ok' : 'ko');
      if (!ok) quiz.querySelector(`input[value="${quiz.dataset.correct}"]`)?.closest('label').classList.add('ok');

      const why = quiz.querySelector('template.why').innerHTML;
      feedback.innerHTML = `<strong class="${ok ? 'ok' : 'ko'}">${ok ? 'Exact.' : 'Pas tout à fait.'}</strong> ${why}`;
      quiz.querySelectorAll('input').forEach((el) => (el.disabled = true));

      done.add(i);
      if (ok) right.add(i);
      if (score && done.size === quizzes.length) {
        score.textContent = `Score : ${right.size} sur ${quizzes.length}.`;
      }
    });
  });
})();
