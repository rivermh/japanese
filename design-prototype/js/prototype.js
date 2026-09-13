(() => {
  const installExtendedShell = () => {
    const body = document.body;
    if (!body.classList.contains("extended-body")) return;
    const active = body.dataset.active || "";
    const title = body.dataset.pageTitle || "Japanese";
    const links = [
      ["home", "index.html", "홈"], ["today", "today.html", "오늘의 학습"],
      ["dictionary", "dictionary.html", "사전"], ["review", "review.html", "복습"],
      ["my", "my-learning.html", "내 학습"]
    ];
    const desktop = document.querySelector("[data-desktop-nav]");
    if (desktop) desktop.innerHTML = `
      <a class="brand" href="index.html"><span class="brand-mark" lang="ja">日</span><span>Japanese</span></a>
      <nav class="rail-nav" aria-label="주요 메뉴">${links.map(([key, href, label]) => `<a class="rail-link ${active === key ? "active" : ""}" href="${href}">${label}</a>`).join("")}</nav>
      <div class="rail-divider"></div><p class="rail-subhead">바로가기</p>
      <nav class="rail-nav"><a class="rail-link ${active === "weakness" ? "active" : ""}" href="weaknesses.html">약점노트 <span style="margin-left:auto;font-size:10px">8</span></a><a class="rail-link ${active === "saved" ? "active" : ""}" href="saved.html">저장함</a><a class="rail-link ${active === "report" ? "active" : ""}" href="weekly-report.html">주간 리포트</a><a class="rail-link ${active === "progress" ? "active" : ""}" href="progress.html">JLPT 진도</a></nav>
      <div class="rail-footer"><div class="rail-user"><span class="avatar">민</span><span><strong>민수</strong><small>N3 학습 중</small></span></div></div>`;
    const topbar = document.querySelector("[data-topbar]");
    if (topbar) topbar.innerHTML = `<span class="topbar-title">${title}</span><form class="global-search" action="dictionary.html"><input aria-label="사전 검색" placeholder="단어, 읽기, 뜻, 문법 검색"><button aria-label="검색">⌕</button></form><a class="header-link" href="onboarding.html">학습 계획</a><a class="header-cta" href="today.html">오늘 학습</a>`;
    const mobileHeader = document.querySelector("[data-mobile-header]");
    if (mobileHeader) mobileHeader.innerHTML = `<a class="brand" href="index.html"><span class="brand-mark" lang="ja">日</span><span>Japanese</span></a><strong>${title}</strong>`;
    const icons = {
      home: '<path d="M4 10.5 12 4l8 6.5V20h-6v-6h-4v6H4Z"/>',
      today: '<path d="M5 4h14v16H5zM8 2v4m8-4v4M5 9h14"/>',
      dictionary: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 5 5"/>',
      review: '<path d="M4 6h16M4 12h10M4 18h13"/>',
      my: '<path d="M5 20V10m7 10V4m7 16v-7"/>'
    };
    const mobile = document.querySelector("[data-mobile-nav]");
    if (mobile) mobile.innerHTML = links.map(([key, href, label]) => `<a class="${active === key || (["weakness"].includes(active) && key === "review") || (["saved","report","progress"].includes(active) && key === "my") ? "active" : ""}" href="${href}"><svg class="nav-icon" viewBox="0 0 24 24">${icons[key]}</svg><span>${key === "today" ? "오늘" : label}</span></a>`).join("");
  };
  installExtendedShell();

  const stateCopy = {
    idle: ["쉬는 중", "오늘도 옆에서 기다리고 있어요."],
    study: ["공부할 준비", "책을 펴고 오늘 학습을 시작해요."],
    happy: ["기분 좋음", "한 걸음 더 기억해냈네요."],
    goal: ["오늘 목표 완료", "오늘의 약속을 모두 지켰어요."],
    growth: ["성장 직전", "다음 모습까지 180 EXP 남았어요."]
  };

  document.querySelectorAll("[data-haru-state]").forEach((button) => {
    button.addEventListener("click", () => {
      const state = button.dataset.haruState;
      const room = document.querySelector("[data-haru-room]");
      if (!room) return;
      room.dataset.state = state;
      document.querySelectorAll("[data-haru-state]").forEach((item) => item.classList.toggle("active", item === button));
      const title = room.querySelector("[data-haru-title]");
      const message = room.querySelector("[data-haru-message]");
      if (title) title.textContent = stateCopy[state][0];
      if (message) message.textContent = stateCopy[state][1];
    });
  });

  const lessons = [
    {
      activity: "복습", kind: "WORD", kindLabel: "단어", level: "JLPT N5", title: "食べる", reading: "たべる",
      meaning: "먹다, 식사하다", pos: "동사 · 2그룹",
      example: "毎朝、家で朝ご飯を食べます。", exampleReading: "まいあさ、いえで あさごはんを たべます。",
      translation: "매일 아침 집에서 아침밥을 먹습니다."
    },
    {
      activity: "새 학습", kind: "WORD", kindLabel: "단어", level: "JLPT N5", title: "約束", reading: "やくそく",
      meaning: "약속", pos: "명사 · する동사",
      example: "友達との約束を忘れないでください。", exampleReading: "ともだちとの やくそくを わすれないでください。",
      translation: "친구와의 약속을 잊지 마세요."
    },
    {
      activity: "새 학습", kind: "GRAMMAR", kindLabel: "문법", level: "JLPT N4", title: "～ように", reading: "",
      meaning: "~하도록, ~하게", pos: "문법 패턴",
      example: "忘れないように、手帳に書いておきます。", exampleReading: "わすれないように、てちょうに かいておきます。",
      translation: "잊지 않도록 수첩에 적어 둡니다.",
      connection: "동사 사전형 / ない형 + ように", explanation: "목표한 상태가 되거나 되지 않도록 행동할 때 사용합니다."
    }
  ];
  let lessonIndex = 0;

  const renderLesson = () => {
    const root = document.querySelector("[data-lesson]");
    if (!root) return;
    const data = lessons[lessonIndex];
    const set = (name, value) => {
      root.querySelectorAll(`[data-field="${name}"]`).forEach((node) => {
        node.textContent = value || "";
        node.hidden = !value;
      });
    };
    set("activity", data.activity); set("kind", data.kindLabel); set("level", data.level);
    set("title", data.title); set("reading", data.reading); set("meaning", data.meaning);
    set("pos", data.pos); set("example", data.example); set("exampleReading", data.exampleReading);
    set("translation", data.translation); set("connection", data.connection); set("explanation", data.explanation);
    root.querySelectorAll("[data-grammar-only]").forEach((node) => node.hidden = data.kind !== "GRAMMAR");
    document.querySelectorAll("[data-current]").forEach((node) => node.textContent = String(lessonIndex + 1));
    document.querySelectorAll("[data-progress-fill]").forEach((node) => node.style.setProperty("--value", `${((lessonIndex + 1) / lessons.length) * 100}%`));
    const response = document.querySelector("[data-study-reaction]");
    if (response) response.textContent = lessonIndex === 0 ? "천천히 떠올려 봐요." : lessonIndex === 1 ? "좋아요. 문장까지 읽어볼까요?" : "마지막 문법이에요.";
  };

  document.querySelectorAll("[data-study-answer]").forEach((button) => {
    button.addEventListener("click", () => {
      lessonIndex += 1;
      if (lessonIndex < lessons.length) {
        renderLesson();
        window.scrollTo({ top: 0, behavior: "smooth" });
        return;
      }
      document.querySelector("[data-lesson]")?.setAttribute("hidden", "");
      document.querySelector("[data-study-companion]")?.setAttribute("hidden", "");
      document.querySelector("[data-completion]")?.classList.add("visible");
      document.querySelector("[data-study-actions]")?.setAttribute("hidden", "");
      document.querySelectorAll("[data-current]").forEach((node) => node.textContent = String(lessons.length));
      document.querySelectorAll("[data-progress-fill]").forEach((node) => node.style.setProperty("--value", "100%"));
    });
  });
  renderLesson();

  const normalize = (value) => value.trim().toLocaleLowerCase().normalize("NFKC");
  const filterResults = () => {
    const input = document.querySelector("[data-dictionary-input]");
    if (!input) return;
    const keyword = normalize(input.value);
    const selectedKind = document.querySelector("[data-kind-filter].active")?.dataset.kindFilter || "all";
    let visible = 0;
    document.querySelectorAll("[data-result-row]").forEach((row) => {
      const matchesText = !keyword || normalize(row.dataset.search || row.textContent).includes(keyword);
      const matchesKind = selectedKind === "all" || row.dataset.kind === selectedKind;
      const show = matchesText && matchesKind;
      row.hidden = !show;
      if (show) visible += 1;
    });
    document.querySelectorAll("[data-result-count]").forEach((node) => node.textContent = `${visible}개 결과`);
    document.querySelector("[data-empty-results]")?.toggleAttribute("hidden", visible !== 0);
  };
  document.querySelectorAll("[data-dictionary-form]").forEach((form) => form.addEventListener("submit", (event) => { event.preventDefault(); filterResults(); }));
  document.querySelectorAll("[data-kind-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll("[data-kind-filter]").forEach((item) => item.classList.remove("active"));
      button.classList.add("active");
      filterResults();
    });
  });

  const sheet = document.querySelector("[data-filter-sheet]");
  const closeSheet = () => {
    sheet?.classList.remove("open");
    document.body.style.overflow = "";
  };
  document.querySelectorAll("[data-open-filter]").forEach((button) => button.addEventListener("click", () => {
    sheet?.classList.add("open"); document.body.style.overflow = "hidden";
  }));
  document.querySelectorAll("[data-close-filter]").forEach((button) => button.addEventListener("click", closeSheet));
  sheet?.addEventListener("click", (event) => { if (event.target === sheet) closeSheet(); });
  document.addEventListener("keydown", (event) => { if (event.key === "Escape") closeSheet(); });

  const savedTabs = document.querySelector("[data-saved-tabs]");
  savedTabs?.querySelectorAll("[data-tab]").forEach((button) => {
    button.addEventListener("click", () => {
      const selected = button.dataset.tab;
      savedTabs.querySelectorAll("[data-tab]").forEach((item) => item.classList.toggle("active", item === button));
      document.querySelectorAll("[data-panel]").forEach((panel) => { panel.hidden = panel.dataset.panel !== selected; });
    });
  });

  let wizardStep = 1;
  const wizardNext = document.querySelector("[data-wizard-next]");
  const wizardPrev = document.querySelector("[data-wizard-prev]");
  const renderWizard = () => {
    document.querySelectorAll("[data-wizard-step]").forEach((step) => { step.hidden = Number(step.dataset.wizardStep) !== wizardStep; });
    document.querySelectorAll("[data-step-label]").forEach((label) => label.classList.toggle("active", Number(label.dataset.stepLabel) <= wizardStep));
    if (wizardPrev) wizardPrev.disabled = wizardStep === 1;
    if (wizardNext) wizardNext.textContent = wizardStep === 4 ? "오늘 학습 시작 →" : "다음 →";
  };
  wizardNext?.addEventListener("click", () => {
    if (wizardStep === 4) { window.location.href = "today.html"; return; }
    wizardStep += 1; renderWizard(); window.scrollTo({ top: 0, behavior: "smooth" });
  });
  wizardPrev?.addEventListener("click", () => { if (wizardStep > 1) { wizardStep -= 1; renderWizard(); } });
  renderWizard();
})();
