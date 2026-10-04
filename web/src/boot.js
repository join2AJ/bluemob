  // Keep "last seen" times fresh.
  setInterval(() => { if (S.onboarded && S.screen === "main" && (S.tab === "radar" || S.tab === "chats")) refresh(); }, 30000);

  render("fade");
  requestAnimationFrame(drawRadars);
  if (S.onboarded) later(600, startMesh);
})();
</script>
