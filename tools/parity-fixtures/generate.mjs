// Generates golden fixtures for spec 010 parity tests (research R1).
//
// Each scenario starts from a shared base budget, exports it as `before`,
// runs one action through desktop Actual's own handlers, and records the
// rows Actual wrote. Run by hand with `npm run generate`; CI never runs it.
//
// Output: core/budget/src/test/resources/parity/<area>/
//   <base>.zip                 the budget before any scenario action
//   <scenario>.json            { base, today, action, ids, tables, values }

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// --- Fixed clock -----------------------------------------------------------
// Actual reads "today" for starting balances, close transfers, averages and
// schedules. Pin it so fixtures are reproducible. Date.now advances by 1 ms
// per call so sync timestamps stay unique.
const TODAY = '2026-10-07';
const FIXED_MS = Date.parse(`${TODAY}T12:00:00Z`);
let tick = 0;
const RealDate = Date;
class FixedDate extends RealDate {
  constructor(...args) {
    if (args.length === 0) super(FIXED_MS + tick++);
    else super(...args);
  }
  static now() {
    return FIXED_MS + tick++;
  }
}
globalThis.Date = FixedDate;

const api = await import('@actual-app/api');

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const outRoot = path.join(repo, 'core/budget/src/test/resources/parity');
const dataDir = path.join(here, '.data');
const ACTUAL_VERSION = JSON.parse(
  fs.readFileSync(path.join(here, 'node_modules/@actual-app/api/package.json'), 'utf8'),
).version;

fs.rmSync(dataDir, { recursive: true, force: true });
fs.mkdirSync(dataDir, { recursive: true });
const lib = await api.init({ dataDir });
const { send, q, db } = lib;

// --- Helpers ---------------------------------------------------------------

const TABLES = {
  budget: ['zero_budgets', 'reflect_budgets', 'zero_budget_months', 'notes'],
  accounts: ['accounts', 'payees', 'transactions', 'notes', 'categories'],
  rules: ['rules', 'transactions', 'payees'],
  schedules: ['schedules', 'schedules_next_date', 'rules', 'transactions'],
  templates: ['zero_budgets', 'categories', 'notes'],
};

async function dump(names) {
  const out = {};
  for (const name of names) {
    out[name] = await db.all(`SELECT * FROM ${name} ORDER BY id`);
  }
  return out;
}

async function ids() {
  const map = { accounts: {}, categories: {}, groups: {}, payees: {}, schedules: {} };
  for (const row of await db.all('SELECT id, name FROM accounts WHERE tombstone = 0'))
    map.accounts[row.name] = row.id;
  for (const row of await db.all('SELECT id, name FROM categories WHERE tombstone = 0'))
    map.categories[row.name] = row.id;
  for (const row of await db.all('SELECT id, name FROM category_groups WHERE tombstone = 0'))
    map.groups[row.name] = row.id;
  for (const row of await db.all(
    "SELECT id, name FROM payees WHERE tombstone = 0 AND IFNULL(name, '') != ''",
  ))
    map.payees[row.name] = row.id;
  for (const row of await db.all(
    "SELECT id, name FROM schedules WHERE tombstone = 0 AND IFNULL(name, '') != ''",
  ))
    map.schedules[row.name] = row.id;
  return map;
}

async function month(m) {
  // getBudgetMonth reads cells without waiting; recompute and wait first.
  await send('reset-budget-cache');
  const b = await api.getBudgetMonth(m);
  // Keep the sheet-level numbers; categories are covered by the tables.
  const { categoryGroups, ...totals } = b;
  const categories = {};
  for (const group of categoryGroups) {
    for (const c of group.categories) {
      categories[c.name] = {
        budgeted: c.budgeted,
        spent: c.spent,
        balance: c.balance,
        carryover: c.carryover,
      };
    }
  }
  // forNextMonth is the manual hold; auto-hold lives in other cells.
  const cell = async name =>
    (await send('get-cell', { sheetName: `budget${m.replace('-', '')}`, name })).value;
  return {
    ...totals,
    bufferedAuto: await cell('buffered-auto'),
    bufferedSelected: await cell('buffered-selected'),
    categories,
  };
}

let budgetCounter = 0;

// Builds a base budget once and returns a function that opens a fresh copy
// with the same ids for each scenario.
async function makeBase(area, baseName, build) {
  await send('create-budget', { budgetName: `${area}-${baseName}` });
  const budgetId = (await api.getBudgets()).find(b => b.name === `${area}-${baseName}`).id;
  await build();
  const zip = await api.exportBudget();
  fs.mkdirSync(path.join(outRoot, area), { recursive: true });
  fs.writeFileSync(path.join(outRoot, area, `${baseName}.zip`), zip);
  await send('close-budget');
  const source = path.join(dataDir, budgetId);
  return async function openCopy() {
    const copyId = `${budgetId}-copy-${++budgetCounter}`;
    const target = path.join(dataDir, copyId);
    fs.cpSync(source, target, { recursive: true });
    const metaPath = path.join(target, 'metadata.json');
    const meta = JSON.parse(fs.readFileSync(metaPath, 'utf8'));
    meta.id = copyId;
    fs.writeFileSync(metaPath, JSON.stringify(meta));
    await api.loadBudget(copyId);
  };
}

async function scenario(area, base, open, name, { action, tables, values, run }) {
  await open();
  await run();
  const out = {
    scenario: name,
    base: `${base}.zip`,
    actualVersion: ACTUAL_VERSION,
    today: TODAY,
    action,
    ids: await ids(),
    tables: await dump(tables),
    values: values ? await values() : {},
  };
  fs.writeFileSync(
    path.join(outRoot, area, `${name}.json`),
    JSON.stringify(out, null, 2) + '\n',
  );
  await send('close-budget');
  console.log(`  ${area}/${name}`);
}

const byName = async (type, name) => api.getIDByName(type, name);

// --- Base budgets ------------------------------------------------------------

// Envelope month base. Months 2026-07..10, one hidden category, one hidden
// group, a hold in September, an overspend in September, and an income
// category for auto-hold.
async function buildMonthBase({ tracking = false } = {}) {
  if (tracking) {
    await send('preferences/save', { id: 'budgetType', value: 'tracking' });
  }
  const checking = await api.createAccount({ name: 'Checking', offbudget: false }, 0);
  const food = await byName('categories', 'Food');
  const general = await byName('categories', 'General');
  const bills = await byName('categories', 'Bills');
  const usual = (await api.getCategoryGroups()).find(g => g.name === 'Usual Expenses').id;
  const incomeGroup = (await api.getCategoryGroups()).find(g => g.is_income).id;
  const salary = await api.createCategory({ name: 'Salary', group_id: incomeGroup, is_income: true });
  const pets = await api.createCategory({ name: 'Pets', group_id: usual });
  const gym = await api.createCategory({ name: 'Old Gym', group_id: usual, hidden: true });
  const archive = await api.createCategoryGroup({ name: 'Archive', hidden: true });
  const archived = await api.createCategory({ name: 'Archived', group_id: archive });

  const tx = [];
  for (const [m, foodSpent, generalSpent, petsSpent] of [
    ['2026-07', -40000, -9000, 0],
    ['2026-08', -35000, -10000, -2500],
    ['2026-09', -50000, -12000, -3000],
    ['2026-10', -10000, 0, 0],
  ]) {
    tx.push({ date: `${m}-01`, amount: 300000, payee_name: 'Employer', category: salary });
    tx.push({ date: `${m}-03`, amount: -120000, payee_name: 'Landlord', category: bills });
    tx.push({ date: `${m}-05`, amount: foodSpent, payee_name: 'Grocer', category: food });
    if (generalSpent) tx.push({ date: `${m}-06`, amount: generalSpent, payee_name: 'Shop', category: general });
    if (petsSpent) tx.push({ date: `${m}-07`, amount: petsSpent, payee_name: 'Vet', category: pets });
  }
  tx.push({ date: '2026-07-10', amount: -2000, payee_name: 'Gym', category: gym });
  tx.push({ date: '2026-07-11', amount: -1500, payee_name: 'Shop', category: archived });
  await api.addTransactions(checking, tx);

  const budgets = {
    '2026-07': { [food]: 45000, [general]: 10000, [bills]: 120000, [gym]: 3000, [archived]: 2000 },
    '2026-08': { [food]: 40000, [general]: 10000, [bills]: 120000, [pets]: 3000, [gym]: 3000, [archived]: 2000 },
    '2026-09': { [food]: 50000, [general]: 5000, [bills]: 120000, [pets]: 3000, [gym]: 3000, [archived]: 2000 },
    '2026-10': { [food]: 50000 },
  };
  if (tracking) {
    for (const m of Object.keys(budgets)) budgets[m][salary] = 300000;
  }
  await api.batchBudgetUpdates(async () => {
    for (const [m, rows] of Object.entries(budgets)) {
      for (const [cat, amount] of Object.entries(rows)) await api.setBudgetAmount(m, cat, amount);
    }
  });
  if (!tracking) await api.holdBudgetForNextMonth('2026-09', 20000);
}

// Accounts base: on-budget checking and savings with history, an off-budget
// brokerage, a card with a transfer partner, and an empty account.
async function buildAccountsBase() {
  const checking = await api.createAccount({ name: 'Checking', offbudget: false }, 250000);
  const savings = await api.createAccount({ name: 'Savings', offbudget: false }, 100000);
  await api.createAccount({ name: 'Brokerage', offbudget: true }, 500000);
  const card = await api.createAccount({ name: 'Visa', offbudget: false }, 0);
  await api.createAccount({ name: 'Unused', offbudget: false }, 0);
  const zeroed = await api.createAccount({ name: 'Zeroed', offbudget: false }, 0);
  const food = await byName('categories', 'Food');
  await api.addTransactions(card, [
    { date: '2026-09-12', amount: -4500, payee_name: 'Grocer', category: food },
  ]);
  await api.addTransactions(zeroed, [
    { date: '2026-09-01', amount: 1000, payee_name: 'Refund' },
    { date: '2026-09-02', amount: -1000, payee_name: 'Shop' },
  ]);
  // A transfer from checking to the card, so force close has a partner.
  const cardPayee = (await api.getPayees()).find(p => p.transfer_acct === card).id;
  await api.addTransactions(
    checking,
    [{ date: '2026-09-20', amount: -2000, payee: cardPayee }],
    { runTransfers: true },
  );
  void savings;
}

async function buildRulesBase() {
  const checking = await api.createAccount({ name: 'Checking', offbudget: false }, 100000);
  const food = await byName('categories', 'Food');
  // Learning needs 3 of the payee's latest 5 transactions in one category
  // (getProbableCategory), so Cafe has two categorized and one open.
  await api.addTransactions(checking, [
    { date: '2026-08-05', amount: -2800, payee_name: 'Cafe', category: food },
    { date: '2026-08-20', amount: -3100, payee_name: 'Cafe', category: food },
    { date: '2026-09-02', amount: -1200, payee_name: 'Corner Shop' },
    { date: '2026-09-05', amount: -3000, payee_name: 'Cafe' },
  ]);
}

async function buildReportsBase() {
  const checking = await api.createAccount({ name: 'Checking', offbudget: false }, 0);
  const savings = await api.createAccount({ name: 'Savings', offbudget: false }, 0);
  const brokerage = await api.createAccount({ name: 'Brokerage', offbudget: true }, 0);
  const food = await byName('categories', 'Food');
  const bills = await byName('categories', 'Bills');
  const general = await byName('categories', 'General');
  const incomeGroup = (await api.getCategoryGroups()).find(g => g.is_income).id;
  const salary = await api.createCategory({ name: 'Salary', group_id: incomeGroup, is_income: true });
  const payees = await api.getPayees();
  const toSavings = payees.find(p => p.transfer_acct === savings).id;
  const toBrokerage = payees.find(p => p.transfer_acct === brokerage).id;

  const tx = [];
  for (const m of ['2026-06', '2026-07', '2026-08', '2026-09']) {
    tx.push({ date: `${m}-01`, amount: 400000, payee_name: 'Employer', category: salary });
    tx.push({ date: `${m}-03`, amount: -150000, payee_name: 'Landlord', category: bills });
    tx.push({ date: `${m}-09`, amount: -42000, payee_name: 'Grocer', category: food });
    tx.push({ date: `${m}-15`, amount: -8000, payee_name: 'Cafe', category: food });
    tx.push({ date: `${m}-20`, amount: -5000, payee_name: 'Shop' });
  }
  // A split: one parent, two children.
  tx.push({
    date: '2026-08-22',
    amount: -10000,
    payee_name: 'Market',
    subtransactions: [
      { amount: -6000, category: food },
      { amount: -4000, category: general },
    ],
  });
  await api.addTransactions(checking, tx);
  // Transfers: on-budget → on-budget, and on-budget → off-budget.
  await api.addTransactions(
    checking,
    [
      { date: '2026-07-25', amount: -50000, payee: toSavings },
      { date: '2026-08-25', amount: -70000, payee: toBrokerage },
    ],
    { runTransfers: true },
  );
  // A deleted transaction that reports must ignore.
  await api.addTransactions(checking, [
    { date: '2026-09-10', amount: -99900, payee_name: 'Mistake', category: general },
  ]);
  const mistake = (await api.getTransactions(checking, '2026-09-10', '2026-09-10'))[0];
  await api.deleteTransaction(mistake.id);
  await api.addTransactions(brokerage, [
    { date: '2026-09-30', amount: 12500, payee_name: 'Dividend' },
  ]);
}

async function buildTemplatesBase() {
  await send('preferences/save', { id: 'flags.goalTemplatesEnabled', value: 'true' });
  const checking = await api.createAccount({ name: 'Checking', offbudget: false }, 0);
  const usual = (await api.getCategoryGroups()).find(g => g.name === 'Usual Expenses').id;
  const incomeGroup = (await api.getCategoryGroups()).find(g => g.is_income).id;
  const salary = await api.createCategory({ name: 'Salary', group_id: incomeGroup, is_income: true });
  const names = [
    'Fixed', 'Up To', 'By Date', 'Rent', 'Percent', 'Average', 'Remainder A',
    'Remainder B', 'Goal', 'Priority', 'Already Set', 'Hidden Tmpl',
  ];
  const cat = {};
  for (const name of names) {
    cat[name] = await api.createCategory({
      name,
      group_id: usual,
      hidden: name === 'Hidden Tmpl',
    });
  }
  const tx = [];
  for (const m of ['2026-07', '2026-08', '2026-09', '2026-10']) {
    tx.push({ date: `${m}-01`, amount: 500000, payee_name: 'Employer', category: salary });
  }
  for (const [m, amt] of [['2026-07', -9000], ['2026-08', -12000], ['2026-09', -15000]]) {
    tx.push({ date: `${m}-14`, amount: amt, payee_name: 'Store', category: cat['Average'] });
  }
  await api.addTransactions(checking, tx);

  const landlord = (await api.getPayees()).find(p => p.name === 'Employer') && (await api.createPayee({ name: 'Landlord' }));
  await send('schedule/create', {
    schedule: { name: 'Rent', posts_transaction: false },
    conditions: [
      { op: 'is', field: 'account', value: checking },
      { op: 'is', field: 'payee', value: landlord },
      { op: 'is', field: 'amount', value: -150000 },
      {
        op: 'isapprox',
        field: 'date',
        value: { start: '2026-10-28', frequency: 'monthly', interval: 1, endMode: 'never', patterns: [], skipWeekend: false, weekendSolveMode: 'after' },
      },
    ],
  });

  await api.setBudgetAmount('2026-10', cat['Already Set'], 1234);
  const notes = {
    Fixed: '#template 50',
    'Up To': '#template up to 200',
    'By Date': '#template 600 by 2026-12 repeat every year',
    Rent: '#template schedule Rent',
    Percent: '#template 10% of Salary',
    Average: '#template average 3 months',
    'Remainder A': '#template remainder',
    'Remainder B': '#template remainder 2',
    Goal: '#goal 500',
    Priority: '#template-2 75',
    'Already Set': '#template 99',
    'Hidden Tmpl': '#template 40',
  };
  for (const [name, note] of Object.entries(notes)) await api.updateNote(cat[name], note);
}

// --- Scenarios ---------------------------------------------------------------

async function monthScenarios() {
  console.log('month');
  const area = 'month';
  const open = await makeBase(area, 'envelope-base', () => buildMonthBase());
  const base = 'envelope-base';
  const months = async () => ({
    '2026-09': await month('2026-09'),
    '2026-10': await month('2026-10'),
    '2026-11': await month('2026-11'),
  });
  const s = (name, spec) => scenario(area, base, open, name, { tables: TABLES.budget, values: months, ...spec });

  await s('breakdown', { action: { kind: 'none' }, run: async () => {} });
  await s('copy-previous-month', {
    action: { handler: 'budget/copy-previous-month', args: { month: '2026-10' } },
    run: () => send('budget/copy-previous-month', { month: '2026-10' }),
  });
  await s('copy-nothing', {
    action: { handler: 'budget/copy-previous-month', args: { month: '2026-07' } },
    run: () => send('budget/copy-previous-month', { month: '2026-07' }),
  });
  await s('set-3month-avg', {
    action: { handler: 'budget/set-3month-avg', args: { month: '2026-10' } },
    run: () => send('budget/set-3month-avg', { month: '2026-10' }),
  });
  await s('set-zero', {
    action: { handler: 'budget/set-zero', args: { month: '2026-10' } },
    run: () => send('budget/set-zero', { month: '2026-10' }),
  });
  await s('transfer-to-budget', {
    action: { handler: 'budget/transfer-category', args: { month: '2026-10', amount: 4000, from: 'Food', to: 'to-budget' } },
    run: async () =>
      send('budget/transfer-category', {
        month: '2026-10',
        amount: 4000,
        from: await byName('categories', 'Food'),
        to: 'to-budget',
        currencyCode: 'USD',
      }),
  });
  await s('auto-hold-on', {
    action: { handler: 'budget/set-carryover', args: { startMonth: '2026-10', category: 'Salary', flag: true } },
    run: async () =>
      send('budget/set-carryover', { startMonth: '2026-10', category: await byName('categories', 'Salary'), flag: true }),
  });
  await s('auto-hold-off-this-month', {
    action: {
      steps: [
        { handler: 'budget/set-carryover', args: { startMonth: '2026-10', category: 'Salary', flag: true } },
        { handler: 'budget/reset-income-carryover', args: { month: '2026-11' } },
      ],
    },
    run: async () => {
      await send('budget/set-carryover', { startMonth: '2026-10', category: await byName('categories', 'Salary'), flag: true });
      await send('budget/reset-income-carryover', { month: '2026-11' });
    },
  });
  await s('auto-hold-off', {
    action: {
      steps: [
        { handler: 'budget/set-carryover', args: { startMonth: '2026-10', category: 'Salary', flag: true } },
        { handler: 'budget/set-carryover', args: { startMonth: '2026-10', category: 'Salary', flag: false } },
      ],
    },
    run: async () => {
      const salary = await byName('categories', 'Salary');
      await send('budget/set-carryover', { startMonth: '2026-10', category: salary, flag: true });
      await send('budget/set-carryover', { startMonth: '2026-10', category: salary, flag: false });
    },
  });

  const openTracking = await makeBase(area, 'tracking-base', () => buildMonthBase({ tracking: true }));
  const t = (name, spec) =>
    scenario(area, 'tracking-base', openTracking, name, { tables: TABLES.budget, values: months, ...spec });
  await t('tracking-copy-previous-month', {
    action: { handler: 'budget/copy-previous-month', args: { month: '2026-10' } },
    run: () => send('budget/copy-previous-month', { month: '2026-10' }),
  });
  await t('tracking-set-zero', {
    action: { handler: 'budget/set-zero', args: { month: '2026-10' } },
    run: () => send('budget/set-zero', { month: '2026-10' }),
  });
}

async function accountScenarios() {
  console.log('accounts');
  const area = 'accounts';
  const base = 'base';
  const open = await makeBase(area, base, buildAccountsBase);
  const values = async () => ({
    balances: Object.fromEntries(
      await Promise.all(
        (await api.getAccounts()).map(async a => [a.name, await api.getAccountBalance(a.id)]),
      ),
    ),
    '2026-10': await month('2026-10'),
  });
  const s = (name, spec) => scenario(area, base, open, name, { tables: TABLES.accounts, values, ...spec });
  const acct = name => byName('accounts', name);

  await s('create-on-budget-with-balance', {
    action: { handler: 'account-create', args: { name: 'Wallet', balance: 500, offBudget: false } },
    run: () => send('account-create', { name: 'Wallet', balance: 500, offBudget: false }),
  });
  await s('create-off-budget-with-balance', {
    action: { handler: 'account-create', args: { name: 'House', balance: 250000, offBudget: true } },
    run: () => send('account-create', { name: 'House', balance: 250000, offBudget: true }),
  });
  await s('create-zero', {
    action: { handler: 'account-create', args: { name: 'Empty', balance: 0, offBudget: false } },
    run: () => send('account-create', { name: 'Empty', balance: 0, offBudget: false }),
  });
  await s('rename-and-note', {
    action: {
      steps: [
        { handler: 'account-update', args: { account: 'Savings', name: 'Rainy Day' } },
        { handler: 'notes-save', args: { id: 'account-<Savings>', note: 'Six months of expenses' } },
      ],
    },
    run: async () => {
      const id = await acct('Savings');
      await send('account-update', { id, name: 'Rainy Day' });
      await send('notes-save', { id: `account-${id}`, note: 'Six months of expenses' });
    },
  });
  await s('reorder', {
    action: { handler: 'account-move', args: { id: 'Visa', targetId: 'Checking' } },
    run: async () => send('account-move', { id: await acct('Visa'), targetId: await acct('Checking') }),
  });
  await s('close-no-transactions', {
    action: { handler: 'account-close', args: { id: 'Unused' } },
    run: async () => send('account-close', { id: await acct('Unused') }),
  });
  await s('close-zero-balance', {
    action: { handler: 'account-close', args: { id: 'Zeroed' } },
    run: async () => send('account-close', { id: await acct('Zeroed') }),
  });
  await s('close-on-to-on', {
    action: { handler: 'account-close', args: { id: 'Savings', transferAccountId: 'Checking' } },
    run: async () => send('account-close', { id: await acct('Savings'), transferAccountId: await acct('Checking') }),
  });
  await s('close-on-to-off-with-category', {
    action: { handler: 'account-close', args: { id: 'Savings', transferAccountId: 'Brokerage', categoryId: 'General' } },
    run: async () =>
      send('account-close', {
        id: await acct('Savings'),
        transferAccountId: await acct('Brokerage'),
        categoryId: await byName('categories', 'General'),
      }),
  });
  await s('force-close', {
    action: { handler: 'account-close', args: { id: 'Visa', forced: true } },
    run: async () => send('account-close', { id: await acct('Visa'), forced: true }),
  });
  await s('close-then-reopen', {
    action: {
      steps: [
        { handler: 'account-close', args: { id: 'Savings', transferAccountId: 'Checking' } },
        { handler: 'account-reopen', args: { id: 'Savings' } },
      ],
    },
    run: async () => {
      const id = await acct('Savings');
      await send('account-close', { id, transferAccountId: await acct('Checking') });
      await send('account-reopen', { id });
    },
  });
}

async function ruleScenarios() {
  console.log('schedules-rules');
  const area = 'schedules-rules';
  const base = 'base';
  const open = await makeBase(area, base, buildRulesBase);
  const s = (name, spec) => scenario(area, base, open, name, spec);
  const ids = {
    checking: () => byName('accounts', 'Checking'),
    payee: name => byName('payees', name),
    cat: name => byName('categories', name),
  };

  await s('rule-payee-category', {
    tables: TABLES.rules,
    action: {
      steps: [
        { handler: 'rule-add', args: { stage: null, conditionsOp: 'and', conditions: [{ op: 'is', field: 'payee', value: 'Corner Shop' }], actions: [{ op: 'set', field: 'category', value: 'Food' }] } },
        { handler: 'api/addTransactions', args: { account: 'Checking', date: '2026-10-02', amount: -800, payee: 'Corner Shop' } },
      ],
    },
    run: async () => {
      await send('rule-add', {
        stage: null,
        conditionsOp: 'and',
        conditions: [{ op: 'is', field: 'payee', value: await ids.payee('Corner Shop'), type: 'id' }],
        actions: [{ op: 'set', field: 'category', value: await ids.cat('Food'), type: 'id' }],
      });
      await api.addTransactions(await ids.checking(), [
        { date: '2026-10-02', amount: -800, payee: await ids.payee('Corner Shop') },
      ], { runTransfers: true });
      // addTransactions skips rules; apply them the way the desktop import path does.
      const added = (await api.getTransactions(await ids.checking(), '2026-10-02', '2026-10-02'))[0];
      const run = await send('rules-run', { transaction: added });
      await api.updateTransaction(added.id, { category: run.category });
    },
  });
  await s('rule-kept-from-file', {
    tables: TABLES.rules,
    action: { handler: 'rule-add', args: { note: 'conditions on imported_payee and an op the phone does not edit' } },
    run: async () => {
      await send('rule-add', {
        stage: 'pre',
        conditionsOp: 'or',
        conditions: [
          { op: 'contains', field: 'imported_payee', value: 'CORNER', type: 'imported_payee' },
          { op: 'matches', field: 'notes', value: '^ref-[0-9]+$', type: 'string' },
        ],
        actions: [
          { op: 'set', field: 'payee', value: await ids.payee('Corner Shop'), type: 'id' },
          { op: 'append-notes', field: 'notes', value: ' (auto)' },
        ],
      });
    },
  });
  await s('schedule-monthly-autopost', {
    tables: TABLES.schedules,
    action: {
      steps: [
        { handler: 'schedule/create', args: { name: 'Internet', posts_transaction: true, start: TODAY, frequency: 'monthly' } },
        { handler: 'schedule/force-run-service' },
        { handler: 'schedule/force-run-service', note: 'second run must not post again' },
      ],
    },
    run: async () => {
      await send('schedule/create', {
        schedule: { name: 'Internet', posts_transaction: true },
        conditions: [
          { op: 'is', field: 'account', value: await ids.checking() },
          { op: 'is', field: 'payee', value: await ids.payee('Cafe') },
          { op: 'is', field: 'amount', value: -6000 },
          {
            op: 'isapprox',
            field: 'date',
            value: { start: TODAY, frequency: 'monthly', interval: 1, endMode: 'never', patterns: [], skipWeekend: false, weekendSolveMode: 'after' },
          },
        ],
      });
      await send('schedule/force-run-service');
      await send('schedule/force-run-service');
    },
  });
  await s('schedule-pattern-kept', {
    tables: TABLES.schedules,
    action: { handler: 'schedule/create', args: { name: 'Payday', frequency: 'monthly', patterns: 'last Friday', skipWeekend: true } },
    run: async () => {
      await send('schedule/create', {
        schedule: { name: 'Payday', posts_transaction: false },
        conditions: [
          { op: 'is', field: 'account', value: await ids.checking() },
          { op: 'isbetween', field: 'amount', value: { num1: 290000, num2: 310000 } },
          {
            op: 'isapprox',
            field: 'date',
            value: {
              start: '2026-10-01',
              frequency: 'monthly',
              interval: 1,
              endMode: 'after_n_occurrences',
              endOccurrences: 12,
              patterns: [{ type: 'FR', value: -1 }],
              skipWeekend: true,
              weekendSolveMode: 'before',
            },
          },
        ],
      });
    },
  });
  for (const learn of [true, false]) {
    await s(learn ? 'learning-on' : 'learning-off', {
      tables: TABLES.rules,
      action: {
        handler: 'transactions-batch-update',
        args: { updated: [{ payee: 'Cafe', category: 'Food' }], learnCategories: learn },
        pref: { 'learn-categories': String(learn) },
      },
      run: async () => {
        await send('preferences/save', { id: 'learn-categories', value: String(learn) });
        const cafe = (await api.getTransactions(await ids.checking(), '2026-09-05', '2026-09-05'))[0];
        await send('transactions-batch-update', {
          updated: [{ id: cafe.id, category: await ids.cat('Food') }],
          learnCategories: learn,
        });
      },
    });
  }
}

async function reportScenarios() {
  console.log('reports');
  const area = 'reports';
  const base = 'base';
  const open = await makeBase(area, base, buildReportsBase);
  // Queries copied from desktop-client 26.8.1
  // src/components/reports/spreadsheets/{cash-flow-spreadsheet.tsx,
  // net-worth-spreadsheet.ts, makeQuery.ts}. Expected values are what
  // Actual's query engine returns for them; aggregation into report rows is
  // the phone's job and is tested against these.
  const range = { start: '2026-06', end: '2026-09' };
  const values = async () => {
    const start = `${range.start}-01`;
    const end = '2026-09-30';
    const simple = cond =>
      q('transactions')
        .filter({
          $and: [{ date: { $gte: start } }, { date: { $lte: end } }],
          'account.offbudget': false,
          'payee.transfer_acct': null,
        })
        .filter(cond)
        .calculate({ $sum: '$amount' });
    const byMonth = cond =>
      q('transactions')
        .filter({
          $and: [
            { date: { $transform: '$month', $gte: start } },
            { date: { $transform: '$month', $lte: end } },
          ],
          'account.offbudget': false,
        })
        .filter(cond)
        .groupBy([{ $month: '$date' }, 'payee.transfer_acct'])
        .select([{ date: { $month: '$date' } }, { isTransfer: 'payee.transfer_acct' }, { amount: { $sum: '$amount' } }]);
    const cashFlow = {
      income: (await api.aqlQuery(simple({ amount: { $gt: 0 } }))).data,
      expense: (await api.aqlQuery(simple({ amount: { $lt: 0 } }))).data,
      monthlyIncome: (await api.aqlQuery(byMonth({ amount: { $gt: 0 } }))).data,
      monthlyExpense: (await api.aqlQuery(byMonth({ amount: { $lt: 0 } }))).data,
    };
    const netWorth = {};
    for (const a of await api.getAccounts()) {
      netWorth[a.name] = {
        offbudget: a.offbudget,
        starting: (await api.aqlQuery(
          q('transactions').filter({ account: a.id, date: { $lt: start } }).calculate({ $sum: '$amount' }),
        )).data,
        monthly: (await api.aqlQuery(
          q('transactions')
            .filter({ account: a.id, $and: [{ date: { $gte: start } }, { date: { $lte: end } }] })
            .groupBy({ $month: '$date' })
            .select([{ date: { $month: '$date' } }, { amount: { $sum: '$amount' } }]),
        )).data,
      };
    }
    const spendingRows = (await api.aqlQuery(
      q('transactions')
        .filter({
          $and: [
            { date: { $transform: '$month', $gte: range.start } },
            { date: { $transform: '$month', $lte: range.end } },
          ],
        })
        .filter({ amount: { $lt: 0 } })
        .groupBy([{ $month: '$date' }, { $id: '$account' }, { $id: '$payee' }, { $id: '$category' }, { $id: '$payee.transfer_acct.id' }])
        .select([
          { date: { $month: '$date' } },
          { category: { $id: '$category.id' } },
          { categoryHidden: { $id: '$category.hidden' } },
          { categoryIncome: { $id: '$category.is_income' } },
          { categoryGroup: { $id: '$category.group.id' } },
          { categoryGroupHidden: { $id: '$category.group.hidden' } },
          { account: { $id: '$account.id' } },
          { accountOffBudget: { $id: '$account.offbudget' } },
          { payee: { $id: '$payee.id' } },
          { transferAccount: { $id: '$payee.transfer_acct.id' } },
          { amount: { $sum: '$amount' } },
        ]),
    )).data;
    return { range, cashFlow, netWorth, spendingRows };
  };
  await scenario(area, base, open, 'range-2026-06-to-09', {
    tables: [],
    action: { kind: 'read', range },
    values,
    run: async () => {},
  });
  range.start = '2025-01';
  range.end = '2025-03';
  await scenario(area, base, open, 'empty-range', {
    tables: [],
    action: { kind: 'read', range: { ...range } },
    values,
    run: async () => {},
  });
}

async function templateScenarios() {
  console.log('templates');
  const area = 'templates';
  const base = 'base';
  const open = await makeBase(area, base, buildTemplatesBase);
  const values = async () => ({ '2026-10': await month('2026-10') });
  const s = (name, spec) => scenario(area, base, open, name, { tables: TABLES.templates, values, ...spec });
  await s('check-valid', {
    action: { handler: 'budget/check-templates' },
    values: async () => ({ check: await send('budget/check-templates'), '2026-10': await month('2026-10') }),
    run: async () => {},
  });
  await s('check-invalid', {
    action: { steps: [{ handler: 'notes-save', args: { id: 'Fixed', note: '#template fifty dollars' } }, { handler: 'budget/check-templates' }] },
    values: async () => ({ check: await send('budget/check-templates'), '2026-10': await month('2026-10') }),
    run: async () => send('notes-save', { id: await byName('categories', 'Fixed'), note: '#template fifty dollars' }),
  });
  await s('apply', {
    action: { handler: 'budget/apply-goal-template', args: { month: '2026-10' } },
    run: () => send('budget/apply-goal-template', { month: '2026-10' }),
  });
  await s('overwrite', {
    action: { handler: 'budget/overwrite-goal-template', args: { month: '2026-10' } },
    run: () => send('budget/overwrite-goal-template', { month: '2026-10' }),
  });
}

async function undoScenarios() {
  console.log('undo');
  // Undo on the phone restores the rows from before an action. Desktop's
  // undo is client-side, so the fixture records one assignment's before and
  // after; the phone test undoes "after" and compares with "before".
  const area = 'undo';
  const base = 'base';
  const open = await makeBase(area, base, () => buildMonthBase());
  await scenario(area, base, open, 'assign', {
    tables: TABLES.budget,
    action: { handler: 'budget/budget-amount', args: { month: '2026-10', category: 'General', amount: 7500 } },
    values: async () => ({ '2026-10': await month('2026-10') }),
    run: async () =>
      send('budget/budget-amount', { month: '2026-10', category: await byName('categories', 'General'), amount: 7500 }),
  });
}

fs.rmSync(outRoot, { recursive: true, force: true });
await monthScenarios();
await accountScenarios();
await ruleScenarios();
await reportScenarios();
await templateScenarios();
await undoScenarios();
await api.shutdown();
fs.rmSync(dataDir, { recursive: true, force: true });
console.log(`done: ${path.relative(repo, outRoot)}`);
