import { expect, test, type Page } from '@playwright/test';

// Estonian prompts keep the fixture free of Cyrillic; the child UI is then in Estonian too.
const WORDS: Record<string, string> = { koer: 'dog', kass: 'cat', lind: 'bird' };
const PIN = '1357';

async function registerParent(page: Page) {
  await page.goto('/register');
  // the UI starts in Russian; switch it to English for readable selectors
  await page.locator('.lang-switch').selectOption('en');
  await page.getByLabel('E-mail').fill(`e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.test`);
  await page.getByLabel('Password').fill('e2e-password-123');
  await page.getByLabel('Parent PIN').fill(PIN);
  await page.getByLabel('Repeat the PIN').fill(PIN);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Children' })).toBeVisible();
}

async function createChild(page: Page) {
  await page.getByRole('link', { name: 'Add the first child' }).click();
  await page.getByLabel('Name').fill('Mari');
  await page.getByText('A1').click();
  await page.getByLabel('Native language').selectOption('et');
  await page.getByLabel('Words per week').fill(String(Object.keys(WORDS).length));
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('heading', { name: 'Mari' })).toBeVisible();
}

async function buildAndLaunchWeek(page: Page) {
  await page.getByRole('button', { name: 'Prepare for this week' }).click();
  for (const [prompt, answer] of Object.entries(WORDS)) {
    await page.getByLabel('Word (Estonian)').fill(prompt);
    await page.getByLabel('Translation (English)').fill(answer);
    await page.getByRole('button', { name: 'Add', exact: true }).click();
    await expect(page.locator('.word__prompt', { hasText: prompt })).toBeVisible();
  }
  await page.getByRole('button', { name: 'Start the week now' }).click();
  await expect(page.getByText('Running')).toBeVisible();
}

test('a parent prepares a week and the child learns it', async ({ page }) => {
  await registerParent(page);
  await createChild(page);
  await buildAndLaunchWeek(page);

  // hand the device over to the child
  await page.getByRole('link', { name: 'Mari' }).click();
  await page.getByRole('button', { name: 'Hand over to the child' }).click();
  await expect(page.getByRole('heading', { name: 'Tere, Mari!' })).toBeVisible();
  await page.getByRole('link', { name: 'Alusta' }).click();

  const prompt = page.locator('.train__prompt');
  const input = page.getByLabel('Sinu vastus');
  let mistakeMade = false;
  let rounds = 1;

  for (let step = 0; step < 10; step++) {
    if (await page.getByRole('heading', { name: 'Kõik sõnad on tehtud!' }).isVisible()) break;
    if (await page.getByRole('button', { name: 'Lähme' }).isVisible()) {
      rounds++;
      await page.getByRole('button', { name: 'Lähme' }).click();
    }
    await expect(prompt).toBeVisible();
    const word = (await prompt.innerText()).trim();
    const right = WORDS[word];
    expect(right, `unknown prompt ${word}`).toBeDefined();

    if (!mistakeMade) {
      // one deliberate mistake: the right spelling stays on screen, then the next word comes
      mistakeMade = true;
      await input.fill(right.split('').reverse().join(''));
      await input.press('Enter');
      await expect(page.getByText('Õigesti on nii:')).toBeVisible();
      await expect(page.getByRole('img', { name: right })).toBeVisible();
      await expect(page.getByText('Õigesti on nii:')).toBeHidden({ timeout: 8_000 });
      continue;
    }
    await input.fill(right);
    await input.press('Enter');
    await expect(page.getByRole('button', { name: /Õige|Väga hea|Tubli/ })).toBeVisible();
    await page.getByRole('button', { name: /Õige|Väga hea|Tubli/ }).click();
    await page.waitForTimeout(100);
  }

  await expect(page.getByRole('heading', { name: 'Kõik sõnad on tehtud!' })).toBeVisible();
  expect(rounds).toBe(2);
  // two words in round 1 (2 points each) and one in round 2 (1 point)
  await expect(page.getByText('5 / 6')).toBeVisible();

  // back to parent mode with the PIN
  await page.getByRole('link', { name: 'Avalehele' }).click();
  await expect(page.getByText('See nädal on tehtud. Tubli!')).toBeVisible();
  await page.getByRole('button', { name: /Lapsevanem/ }).click();
  await page.getByLabel('PIN', { exact: true }).fill(PIN);
  await page.getByRole('button', { name: 'Jätka' }).click();
  await expect(page.getByRole('heading', { name: 'Mari' })).toBeVisible();
  await expect(page.getByText('Weeks finished: 1 of 4')).toBeVisible();
});

test('a wrong PIN keeps the child mode locked', async ({ page }) => {
  await registerParent(page);
  await createChild(page);
  await page.getByRole('button', { name: 'Hand over to the child' }).click();
  await expect(page.getByRole('heading', { name: 'Tere, Mari!' })).toBeVisible();

  await page.goto('/children');
  await expect(page).toHaveURL(/\/child$/);
  await page.getByRole('button', { name: /Lapsevanem/ }).click();
  await page.getByLabel('PIN', { exact: true }).fill('0000');
  await page.getByRole('button', { name: 'Jätka' }).click();
  await expect(page.getByText('Vale PIN.')).toBeVisible();
});
