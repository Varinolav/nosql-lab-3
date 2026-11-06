import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowRight, Building2, CalendarDays, Check, Clock3, DoorOpen,
  LayoutDashboard, LoaderCircle, Plus, RefreshCw, Save, Search, Sparkles,
  ShieldCheck, Trash2, Users, X, XCircle,
} from 'lucide-react'
import { api } from './api'

const DEFAULT_USER_ID = '1'
const buildingNames = { GK: 'Главный корпус', LOMO: 'Корпус Ломоносова' }
const actionNames = { CREATED: 'Заявка создана', APPROVED: 'Заявка одобрена', REJECTED: 'Заявка отклонена', RULE_REVISED: 'Правило обработки изменено', RULE_ASSIGNED: 'Назначено правило обработки' }
const initialSearchForm = { roomId: '', ruleId: '', historyAction: '', updatedFrom: '', updatedTo: '', sortBy: 'updatedAt', direction: 'DESC' }
const requestPageSize = 10
const statusMeta = {
  PENDING: { label: 'Ждёт решения', className: 'bg-[#fff3bd] text-[#765b00]' },
  APPROVED: { label: 'Подтверждено', className: 'bg-[#dff7e8] text-[#15623b]' },
  REJECTED: { label: 'Отклонено', className: 'bg-[#ffe0e6] text-[#8f2444]' },
}

const nowLocal = (offsetHours = 1) => {
  const date = new Date(Date.now() + offsetHours * 60 * 60 * 1000)
  date.setMinutes(Math.ceil(date.getMinutes() / 15) * 15, 0, 0)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

const initialForm = {
  startsAt: nowLocal(1),
  endsAt: nowLocal(2),
  attendeesCount: 4,
  purpose: '',
  tag: '',
  roomId: '',
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function roomName(room) {
  return room ? `${room.building} · ${room.number}` : 'Аудитория'
}

function Toast({ toast, onClose }) {
  if (!toast) return null
  return (
    <div className={`fixed bottom-5 right-5 z-50 flex max-w-sm items-start gap-3 rounded-2xl border-2 border-[#241b20] px-4 py-3 text-sm font-bold shadow-pop ${toast.type === 'error' ? 'bg-[#ffe0e6]' : 'bg-white'}`}>
      {toast.type === 'error' ? <XCircle className="mt-0.5 size-5 text-[#ca315e]" /> : <Check className="mt-0.5 size-5 text-[#198754]" />}
      <span className="flex-1">{toast.message}</span>
      <button onClick={onClose} aria-label="Закрыть уведомление"><X className="size-4" /></button>
    </div>
  )
}

function EmptyState({ icon: Icon, title, text }) {
  return (
    <div className="flex min-h-52 flex-col items-center justify-center rounded-[1.5rem] border-2 border-dashed border-[#241b20]/10 px-6 text-center">
      <div className="mb-4 rounded-2xl bg-[#f7eef1] p-3"><Icon className="size-6 text-[#ff477e]" /></div>
      <p className="font-extrabold">{title}</p>
      <p className="mt-1 max-w-sm text-sm leading-6 text-[#776b71]">{text}</p>
    </div>
  )
}

function BookingView({ rooms, userId, onCreated, notify }) {
  const [form, setForm] = useState(initialForm)
  const [available, setAvailable] = useState([])
  const [loading, setLoading] = useState(false)
  const [searching, setSearching] = useState(false)
  const [searched, setSearched] = useState(false)
  const availabilityVersion = useRef(0)
  const busy = loading || searching

  useEffect(() => () => { availabilityVersion.current += 1 }, [])

  const clearAvailability = () => {
    setAvailable([])
    setSearched(false)
  }

  const update = (field, value) => {
    const changesAvailability = ['startsAt', 'endsAt', 'attendeesCount'].includes(field)
    if (changesAvailability) {
      availabilityVersion.current += 1
      clearAvailability()
      setSearching(false)
    }
    setForm((current) => ({ ...current, [field]: value, ...(changesAvailability ? { roomId: '' } : {}) }))
  }

  const searchAvailable = async (parameters, version, selectedRoomId) => {
    const data = await api.rooms.available({
      startsAt: parameters.startsAt,
      endsAt: parameters.endsAt,
      attendeesCount: String(parameters.attendeesCount),
    })
    if (version !== availabilityVersion.current) return
    setAvailable(data)
    setSearched(true)
    setForm((current) => ({
      ...current,
      roomId: data.some((room) => room.id === selectedRoomId) ? selectedRoomId : '',
    }))
  }

  const findRooms = async () => {
    const version = ++availabilityVersion.current
    clearAvailability()
    setForm((current) => ({ ...current, roomId: '' }))
    setSearching(true)
    try {
      await searchAvailable(form, version, form.roomId)
    } catch (error) {
      if (version === availabilityVersion.current) notify(error.message, 'error')
    } finally {
      if (version === availabilityVersion.current) setSearching(false)
    }
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!form.roomId) return notify('Сначала выбери аудиторию', 'error')
    setLoading(true)
    try {
      const { tag, ...parameters } = form
      await api.requests.create({
        ...parameters,
        ruleId: null,
        tags: tag.split(',').map((value) => value.trim()).filter(Boolean),
        attendeesCount: Number(form.attendeesCount),
        userId: Number(userId),
      })
      notify('Заявка отправлена — держим кулачки ✦')
      setForm((current) => ({ ...initialForm, startsAt: current.startsAt, endsAt: current.endsAt }))
      setAvailable([])
      setSearched(false)
      onCreated()
    } catch (error) { notify(error.message, 'error') }
    finally { setLoading(false) }
  }

  const saveDraft = async () => {
    setLoading(true)
    try {
      await api.drafts.save(userId, { ...form, attendeesCount: Number(form.attendeesCount) })
      notify('Черновик сохранён на 30 минут')
    } catch (error) { notify(error.message, 'error') }
    finally { setLoading(false) }
  }

  const loadDraft = async () => {
    const version = ++availabilityVersion.current
    clearAvailability()
    setForm((current) => ({ ...current, roomId: '' }))
    setSearching(true)
    try {
      const draft = await api.drafts.get(userId)
      if (version !== availabilityVersion.current) return
      const restoredForm = {
        startsAt: draft.startsAt || '',
        endsAt: draft.endsAt || '',
        attendeesCount: draft.attendeesCount ?? '',
        purpose: draft.purpose || '',
        tag: draft.tag || '',
        roomId: '',
      }
      setForm(restoredForm)
      notify('Черновик восстановлен')
      if (restoredForm.startsAt && restoredForm.endsAt
          && new Date(restoredForm.startsAt) < new Date(restoredForm.endsAt)
          && Number(restoredForm.attendeesCount) > 0) {
        await searchAvailable(restoredForm, version, draft.roomId)
      }
    } catch (error) {
      if (version === availabilityVersion.current) notify(error.message, 'error')
    } finally {
      if (version === availabilityVersion.current) setSearching(false)
    }
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.05fr)_minmax(380px,.95fr)]">
      <form onSubmit={submit} className="panel overflow-hidden p-5 sm:p-7">
        <div className="mb-7 flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">новая встреча</p>
            <h2 className="mt-2 font-display text-2xl font-extrabold sm:text-3xl">Найдём твою комнату</h2>
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={loadDraft} disabled={busy} className="btn-soft" title="Загрузить черновик"><RefreshCw className="size-4" /><span className="hidden sm:inline">Вернуть</span></button>
            <button type="button" onClick={saveDraft} disabled={busy} className="btn-soft" title="Сохранить черновик"><Save className="size-4" /><span className="hidden sm:inline">Черновик</span></button>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label><span className="label">Начало</span><input className="field" type="datetime-local" value={form.startsAt} onChange={(e) => update('startsAt', e.target.value)} required /></label>
          <label><span className="label">Окончание</span><input className="field" type="datetime-local" value={form.endsAt} onChange={(e) => update('endsAt', e.target.value)} required /></label>
          <label><span className="label">Сколько вас</span><div className="relative"><Users className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-[#a4939c]" /><input className="field pl-12" type="number" min="1" value={form.attendeesCount} onChange={(e) => update('attendeesCount', e.target.value)} required /></div></label>
          <label><span className="label">Зачем собираетесь</span><input className="field" value={form.purpose} onChange={(e) => update('purpose', e.target.value)} placeholder="Например, защита проекта" required /></label>
          <label><span className="label">Метки через запятую (необязательно)</span><input className="field" value={form.tag} onChange={(e) => update('tag', e.target.value)} placeholder="Например, учёба, Java" /></label>
        </div>

        <button type="button" onClick={findRooms} disabled={busy} className="btn-primary mt-5 w-full sm:w-auto">
          {busy ? <LoaderCircle className="size-5 animate-spin" /> : <Search className="size-5" />} Найти свободные
        </button>

        {searched && (
          <div className="mt-8">
            <div className="mb-3 flex items-center justify-between"><p className="text-sm font-extrabold">Свободно: {available.length}</p><p className="text-xs font-bold text-[#8c7d85]">выбери одну</p></div>
            {available.length ? (
              <div className="grid gap-3 sm:grid-cols-2">
                {available.map((room) => (
                  <button type="button" key={room.id} onClick={() => update('roomId', room.id)} className={`group flex items-center gap-3 rounded-2xl border-2 p-4 text-left transition ${form.roomId === room.id ? 'border-[#ff5d8f] bg-[#fff0f5]' : 'border-[#241b20]/10 hover:border-[#ff5d8f]/50'}`}>
                    <span className={`grid size-11 shrink-0 place-items-center rounded-xl ${form.roomId === room.id ? 'bg-[#ff5d8f] text-white' : 'bg-[#f7eef1]'}`}><DoorOpen className="size-5" /></span>
                    <span className="min-w-0 flex-1"><b className="block">{roomName(room)}</b><small className="text-[#776b71]">{buildingNames[room.building]} · до {room.capacity}</small></span>
                    {form.roomId === room.id && <Check className="size-5 text-[#ff477e]" />}
                  </button>
                ))}
              </div>
            ) : <EmptyState icon={DoorOpen} title="На это время всё занято" text="Попробуй изменить время или уменьшить число участников." />}
          </div>
        )}

        <div className="mt-7 flex items-center justify-between gap-4 border-t border-[#241b20]/10 pt-6">
          <p className="hidden text-sm leading-5 text-[#776b71] sm:block">Заявка уйдёт на подтверждение<br />и появится в общем списке.</p>
          <button type="submit" disabled={busy || !form.roomId} className="btn-primary w-full sm:w-auto">Отправить заявку <ArrowRight className="size-5" /></button>
        </div>
      </form>

      <aside className="relative overflow-hidden rounded-[2rem] bg-[#241b20] p-6 text-white shadow-card sm:p-8">
        <div className="absolute -right-20 -top-20 size-64 rounded-full border-[48px] border-[#ff5d8f]/20" />
        <div className="relative">
          <div className="mb-12 flex items-center justify-between"><span className="rounded-full bg-white/10 px-3 py-1.5 text-xs font-extrabold uppercase tracking-wider">room forecast</span><Sparkles className="size-6 text-[#ffd54b]" /></div>
          <p className="font-display text-[2.5rem] font-extrabold leading-[1.05] sm:text-5xl">Собрались.<br />Обсудили.<br /><span className="text-[#ff799f]">Сделали.</span></p>
          <p className="mt-6 max-w-md text-base leading-7 text-white/65">Без таблиц, чатов и «а кто занимал 302-ю?». Свободные аудитории уже здесь.</p>
          <div className="mt-12 grid grid-cols-2 gap-3">
            <div className="rounded-2xl bg-white/10 p-4"><Building2 className="mb-5 size-5 text-[#ffd54b]" /><b className="block text-2xl">{rooms.length}</b><span className="text-sm text-white/55">активных комнат</span></div>
            <div className="rounded-2xl bg-[#ffd54b] p-4 text-[#241b20]"><Clock3 className="mb-5 size-5" /><b className="block text-2xl">30 мин</b><span className="text-sm text-[#241b20]/60">живёт черновик</span></div>
          </div>
        </div>
      </aside>
    </div>
  )
}

function RequestsView({ rooms, loading, onRefresh, onAction, notify, canReview = false, userId, popularTags = [] }) {
  const [editingRule, setEditingRule] = useState(null)
  const [savingRule, setSavingRule] = useState(false)
  const saveRule = async (event) => {
    event.preventDefault()
    if (!editingRule.ruleId) return notify('Выбери правило', 'error')
    setSavingRule(true)
    try {
      await api.requests.assignRule(editingRule.requestId, editingRule.ruleId)
      setEditingRule(null)
      notify('Правило назначено, пользователь увидит его в заявке')
      onAction()
    } catch (error) { notify(error.message, 'error') }
    finally { setSavingRule(false) }
  }
  const [filter, setFilter] = useState('ALL')
  const [searchForm, setSearchForm] = useState(initialSearchForm)
  const [appliedSearch, setAppliedSearch] = useState({})
  const [rules, setRules] = useState([])
  const [page, setPage] = useState(0)
  const [searchResults, setSearchResults] = useState(null)
  const [searching, setSearching] = useState(true)
  const displayed = searchResults?.content ?? []
  const roomMap = useMemo(() => new Map(rooms.map((room) => [room.id, room])), [rooms])
  const ruleMap = useMemo(() => new Map(rules.map((rule) => [rule.id, rule.name])), [rules])

  useEffect(() => {
    let cancelled = false
    api.rules.all().then((data) => { if (!cancelled) setRules(data) })
      .catch((error) => { if (!cancelled) notify(error.message, 'error') })
    return () => { cancelled = true }
  }, [notify, loading])

  useEffect(() => {
    if (loading) return
    if (!canReview && !/^[1-9]\d*$/.test(String(userId))) {
      setSearchResults(null)
      setSearching(false)
      return
    }
    let cancelled = false
    setSearchResults(null)
    setSearching(true)
    api.requests.search({
      ...appliedSearch,
      status: filter === 'ALL' ? undefined : filter,
      userId: canReview ? undefined : userId,
      page,
      size: requestPageSize,
    })
      .then((data) => {
        if (!cancelled) setSearchResults(data)
      })
      .catch((error) => {
        if (!cancelled) { notify(error.message, 'error'); setSearchResults(null) }
      })
      .finally(() => { if (!cancelled) setSearching(false) })
    return () => { cancelled = true }
  }, [appliedSearch, filter, page, canReview, userId, notify, loading])

  const search = (event) => {
    event.preventDefault()
    const from = searchForm.updatedFrom ? new Date(searchForm.updatedFrom) : null
    const to = searchForm.updatedTo ? new Date(searchForm.updatedTo) : null
    if ((from && Number.isNaN(from.getTime())) || (to && Number.isNaN(to.getTime()))) return notify('Проверь даты изменения', 'error')
    if (from && to && from > to) return notify('Начало периода изменения должно быть не позже окончания', 'error')
    setPage(0)
    setAppliedSearch({
      roomId: searchForm.roomId,
      ruleId: searchForm.ruleId,
      historyAction: searchForm.historyAction,
      updatedFrom: from?.toISOString(),
      updatedTo: to?.toISOString(),
      sortBy: searchForm.sortBy,
      direction: searchForm.direction,
    })
  }

  const clearSearch = () => {
    setPage(0)
    setAppliedSearch({})
    setSearchForm(initialSearchForm)
  }

  const reject = async (item) => {
    const comment = window.prompt('Почему отклоняем заявку?')
    if (!comment?.trim()) return
    try { await api.requests.reject(item.id, comment.trim()); notify('Заявка отклонена'); onAction() }
    catch (error) { notify(error.message, 'error') }
  }

  const approve = async (item) => {
    try { await api.requests.approve(item.id); notify('Бронирование подтверждено'); onAction() }
    catch (error) { notify(error.message, 'error') }
  }

  return (
    <section className="panel p-5 sm:p-7">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div><p className="eyebrow">{canReview ? 'панель подтверждения' : 'мои заявки'}</p><h2 className="mt-2 font-display text-2xl font-extrabold sm:text-3xl">{canReview ? 'Решения — здесь' : 'Что с бронированиями'}</h2></div>
        <button onClick={onRefresh} className="btn-soft"><RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} /> Обновить</button>
      </div>
      {canReview && <div className="mt-6 rounded-2xl bg-[#f7eef1] p-4">
        <h3 className="font-bold">Популярные метки</h3>
        <p className="mt-1 text-sm text-[#776b71]">Количество заявок с каждой меткой по всем статусам, независимо от фильтров списка.</p>
        {popularTags.length ? <ol className="mt-3 flex flex-wrap gap-2">{popularTags.map(({ tag, count }) => <li key={tag} className="rounded-full bg-white px-3 py-1 text-sm"><span className="font-bold">{tag}</span> · {count}</li>)}</ol> : <p className="mt-3 text-sm text-[#776b71]">Заявок с метками пока нет.</p>}
      </div>}
      <form onSubmit={search} className="my-6 grid gap-3 rounded-2xl bg-[#f7eef1] p-4 sm:grid-cols-2 xl:grid-cols-4">
        <label><span className="label">Аудитория</span><select className="field" value={searchForm.roomId} onChange={(e) => setSearchForm((value) => ({ ...value, roomId: e.target.value }))}><option value="">Все аудитории</option>{rooms.map((room) => <option key={room.id} value={room.id}>{roomName(room)}</option>)}</select></label>
        <label><span className="label">Правило обработки</span><select className="field" value={searchForm.ruleId} onChange={(e) => setSearchForm((value) => ({ ...value, ruleId: e.target.value }))}><option value="">Все правила</option>{rules.map((rule) => <option key={rule.id} value={rule.id}>{rule.name}</option>)}</select></label>
        <label><span className="label">Действие в журнале</span><select className="field" value={searchForm.historyAction} onChange={(e) => setSearchForm((value) => ({ ...value, historyAction: e.target.value }))}><option value="">Все действия</option>{Object.entries(actionNames).map(([action, label]) => <option key={action} value={action}>{label}</option>)}</select></label>
        <label><span className="label">Изменено с</span><input className="field" type="datetime-local" value={searchForm.updatedFrom} onChange={(e) => setSearchForm((value) => ({ ...value, updatedFrom: e.target.value }))} /></label>
        <label><span className="label">Изменено по</span><input className="field" type="datetime-local" value={searchForm.updatedTo} onChange={(e) => setSearchForm((value) => ({ ...value, updatedTo: e.target.value }))} /></label>
        <label><span className="label">Сортировать по</span><select className="field" value={searchForm.sortBy} onChange={(e) => setSearchForm((value) => ({ ...value, sortBy: e.target.value }))}><option value="updatedAt">Дате изменения</option><option value="createdAt">Дате создания</option><option value="startsAt">Началу бронирования</option></select></label>
        <label><span className="label">Порядок</span><select className="field" value={searchForm.direction} onChange={(e) => setSearchForm((value) => ({ ...value, direction: e.target.value }))}><option value="DESC">Сначала новые</option><option value="ASC">Сначала старые</option></select></label>
        <div className="flex items-end gap-2"><button className="btn-soft" disabled={searching}>{searching ? <LoaderCircle className="size-4 animate-spin" /> : <Search className="size-4" />}Найти</button><button className="btn-soft" type="button" onClick={clearSearch}>Сбросить</button></div>
        <p className="text-sm text-[#776b71] sm:col-span-2 xl:col-span-4">{searching ? 'Поиск заявок…' : `Найдено ${searchResults?.totalElements ?? 0} · страница ${page + 1} из ${searchResults?.totalPages || 1}`}</p>
      </form>
      <div className="my-6 flex max-w-full gap-2 overflow-x-auto pb-1">
        {[['ALL', 'Все'], ['PENDING', 'На рассмотрении'], ['APPROVED', 'Одобрены'], ['REJECTED', 'Отклонены']].map(([value, label]) => (
          <button key={value} onClick={() => { setFilter(value); setPage(0) }} className={`shrink-0 rounded-full px-4 py-2 text-sm font-extrabold transition ${filter === value ? 'bg-[#241b20] text-white' : 'bg-[#f7eef1] hover:bg-[#eddee4]'}`}>{label}</button>
        ))}
      </div>
      {displayed.length ? <div className="space-y-3">{displayed.map((item) => {
        const room = roomMap.get(item.roomId)
        const meta = statusMeta[item.status]
        return (
          <article key={item.id} className="grid items-center gap-4 rounded-[1.5rem] border border-[#241b20]/10 p-4 sm:grid-cols-[minmax(170px,1fr)_minmax(190px,1.2fr)_auto] sm:p-5">
            <div className="flex items-center gap-3"><span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-[#fff0f5] text-[#ff477e]"><CalendarDays className="size-5" /></span><div><b>{roomName(room)}</b><p className="mt-0.5 text-sm text-[#776b71]">{formatDate(item.startsAt)} — {new Date(item.endsAt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}</p></div></div>
            <div><p className="font-bold">{item.purpose}</p>{item.tags?.length > 0 && <div className="mt-2 flex flex-wrap gap-2">{item.tags.map((tag) => <span key={tag} className="rounded-full bg-[#f7eef1] px-3 py-1 text-xs font-bold">{tag}</span>)}</div>}{item.ruleId && <p className="mt-2 text-sm text-[#776b71]">Правило: {ruleMap.get(item.ruleId) || item.ruleId}</p>}{item.ruleId && <p className="mt-1 whitespace-pre-wrap break-words text-sm text-[#776b71]">{rules.find((rule) => rule.id === item.ruleId)?.description}</p>}<p className="mt-1 text-sm text-[#776b71]">{item.attendeesCount} чел. · заявка #{item.id}{canReview && ` · пользователь #${item.userId}`}</p>{item.reviewerComment && <p className="mt-2 text-sm font-semibold text-[#a42f52]">«{item.reviewerComment}»</p>}</div>
            <div className="flex flex-wrap items-center gap-2 sm:justify-end">
              <span className={`rounded-full px-3 py-2 text-xs font-extrabold ${meta.className}`}>{meta.label}</span>
              {canReview && item.status === 'PENDING' && <button className="btn-soft" onClick={() => setEditingRule({ requestId: item.id, ruleId: item.ruleId || '' })}>{item.ruleId ? 'Сменить правило' : 'Назначить правило'}</button>}
              {canReview && item.status === 'PENDING' && <><button onClick={() => approve(item)} className="grid size-10 place-items-center rounded-xl bg-[#dff7e8] text-[#15623b] transition hover:scale-105" title="Одобрить"><Check className="size-5" /></button><button onClick={() => reject(item)} className="grid size-10 place-items-center rounded-xl bg-[#ffe0e6] text-[#9b2548] transition hover:scale-105" title="Отклонить"><X className="size-5" /></button></>}
            </div>
            <details className="min-w-0 border-t border-[#241b20]/10 pt-3 sm:col-span-3">
              <summary className="cursor-pointer text-sm font-bold">Журнал действий ({item.history?.length ?? 0})</summary>
              {item.history?.length ? <ol className="mt-3 space-y-3 text-sm">
                {item.history.map((entry, index) => <li key={index}>
                  <div className="flex flex-wrap gap-x-3 gap-y-1">
                    <span className="font-semibold">{actionNames[entry.action] || entry.action}</span>
                    <time className="text-[#776b71]" dateTime={entry.occurredAt}>{formatDate(entry.occurredAt)}</time>
                  </div>
                  {entry.comment && <p className="mt-1 break-words text-[#776b71]">{entry.comment}</p>}
                </li>)}
              </ol> : <p className="mt-3 text-sm text-[#776b71]">Для этой заявки журнал ещё не велся.</p>}
            </details>
          </article>
        )
      })}</div> : !searching && <EmptyState icon={CalendarDays} title="Заявок пока нет" text="Поменяй фильтр или создай первую бронь." />}
      {editingRule && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"><form onSubmit={saveRule} className="panel w-full max-w-lg p-6"><h3 className="text-xl font-extrabold">Назначить правило</h3><label className="mt-4 block"><span className="label">Правило обработки</span><select className="field" required disabled={savingRule} value={editingRule.ruleId} onChange={(event) => setEditingRule((value) => ({ ...value, ruleId: event.target.value }))}><option value="">Выбери правило</option>{rules.filter((rule) => rule.active).map((rule) => <option key={rule.id} value={rule.id}>{rule.name}</option>)}</select></label><p className="mt-3 whitespace-pre-wrap text-sm text-[#776b71]">{rules.find((rule) => rule.id === editingRule.ruleId)?.description}</p>{!rules.some((rule) => rule.active) && <p className="mt-3">Сначала создай правило в разделе «Правила».</p>}<div className="mt-5 flex gap-3"><button className="btn-primary" disabled={savingRule || !editingRule.ruleId}>{savingRule ? 'Сохраняем…' : 'Назначить'}</button><button className="btn-soft" type="button" disabled={savingRule} onClick={() => setEditingRule(null)}>Отмена</button></div></form></div>}
      {(searchResults?.totalPages ?? 0) > 1 && <div className="mt-6 flex items-center justify-center gap-3">
        <button className="btn-soft" disabled={page === 0 || searching} onClick={() => setPage((value) => value - 1)}>Назад</button>
        <span className="text-sm font-bold">{page + 1} / {searchResults.totalPages}</span>
        <button className="btn-soft" disabled={page + 1 >= searchResults.totalPages || searching} onClick={() => setPage((value) => value + 1)}>Дальше</button>
      </div>}
    </section>
  )
}

function RulesView({ notify }) {
  const [rules, setRules] = useState([])
  const [form, setForm] = useState({ name: '', description: '', comment: '' })
  const [editingId, setEditingId] = useState(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const load = useCallback(async () => {
    setLoading(true)
    try { setRules(await api.rules.all()) }
    catch (error) { notify(error.message, 'error') }
    finally { setLoading(false) }
  }, [notify])
  useEffect(() => { load() }, [load])
  const reset = () => { setEditingId(null); setForm({ name: '', description: '', comment: '' }) }
  const submit = async (event) => {
    event.preventDefault()
    if (!form.name.trim() || (editingId && !form.comment.trim())) return notify('Заполни обязательные поля', 'error')
    setBusy(true)
    try {
      const data = { name: form.name.trim(), description: form.description.trim(), comment: form.comment.trim() }
      if (editingId) await api.rules.revise(editingId, data)
      else await api.rules.create(data)
      notify(editingId ? 'Правило изменено, журналы связанных заявок обновлены' : 'Правило создано')
      reset()
      await load()
    } catch (error) { notify(error.message, 'error') }
    finally { setBusy(false) }
  }
  return <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
    <section className="panel p-5 sm:p-7">
      <div className="flex items-center justify-between gap-3"><h2 className="text-2xl font-extrabold">Правила обработки</h2><button className="btn-soft" onClick={load} disabled={loading || busy}>Обновить</button></div>
      <p className="mt-3 text-sm text-[#776b71]">Назначай правила ожидающим заявкам в разделе «Подтверждение». Пользователь увидит условия в своей заявке.</p>
      <div className="mt-6 space-y-3">{rules.map((rule) => <article className="rounded-2xl border border-[#241b20]/10 p-4" key={rule.id}>
        <div className="flex items-center justify-between gap-3"><h3 className="font-bold">{rule.name}</h3><span className="text-xs text-[#776b71]">{rule.active ? 'Активно' : 'Неактивно'}</span></div>
        <p className="mt-2 whitespace-pre-wrap break-words text-sm text-[#776b71]">{rule.description || 'Без описания'}</p>
        <p className="mt-3 text-xs text-[#776b71]">Изменено: {formatDate(rule.updatedAt)}</p>
        <button className="btn-soft mt-3" disabled={busy} onClick={() => { setEditingId(rule.id); setForm({ name: rule.name, description: rule.description || '', comment: '' }) }}>Редактировать</button>
      </article>)}</div>
      {loading && <p className="mt-4">Загрузка правил…</p>}{!loading && !rules.length && <p className="mt-4 text-[#776b71]">Правил пока нет. Создай первое в форме.</p>}
    </section>
    <form className="panel h-fit p-5 sm:p-7" onSubmit={submit}>
      <h3 className="text-xl font-extrabold">{editingId ? 'Редактировать правило' : 'Добавить правило'}</h3>
      {editingId && <p className="mt-3 text-sm text-[#776b71]">Изменение затронет все связанные заявки и будет записано в журнал каждой из них.</p>}
      {[['name', 'Название'], ['description', 'Описание'], ...(editingId ? [['comment', 'Причина изменения']] : [])].map(([key, label]) => <label key={key} className="mt-4 block"><span className="label">{label}</span><textarea className="field" rows={key === 'name' ? 1 : 3} required={key !== 'description'} disabled={busy} value={form[key]} onChange={(event) => setForm({ ...form, [key]: event.target.value })} /></label>)}
      <div className="mt-5 flex flex-wrap gap-3"><button className="btn-primary" disabled={busy}>{busy ? 'Сохраняем…' : editingId ? 'Сохранить' : 'Создать правило'}</button>{editingId && <button className="btn-soft" type="button" disabled={busy} onClick={reset}>Отмена</button>}</div>
    </form>
  </div>
}
function RoomsView({ rooms, onRefresh, notify }) {
  const [form, setForm] = useState({ building: 'GK', number: '', capacity: '' })
  const [busy, setBusy] = useState(false)
  const submit = async (event) => {
    event.preventDefault(); setBusy(true)
    try {
      await api.rooms.create({ ...form, number: Number(form.number), capacity: Number(form.capacity) })
      setForm({ building: 'GK', number: '', capacity: '' }); notify('Аудитория добавлена'); onRefresh()
    } catch (error) { notify(error.message, 'error') }
    finally { setBusy(false) }
  }
  const remove = async (room) => {
    if (!window.confirm(`Убрать аудиторию ${roomName(room)} из доступных?`)) return
    try { await api.rooms.remove(room.id); notify('Аудитория деактивирована'); onRefresh() }
    catch (error) { notify(error.message, 'error') }
  }
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <section className="panel p-5 sm:p-7"><p className="eyebrow">пространства</p><h2 className="mt-2 font-display text-2xl font-extrabold sm:text-3xl">Аудитории</h2>
        <div className="mt-7 grid gap-3 sm:grid-cols-2">{rooms.map((room) => <article key={room.id} className="group flex items-center gap-4 rounded-[1.5rem] border border-[#241b20]/10 p-4 transition hover:border-[#ff5d8f]/50 hover:bg-[#fffafb]"><span className="grid size-14 shrink-0 place-items-center rounded-2xl bg-[#ffd54b]"><DoorOpen className="size-6" /></span><div className="min-w-0 flex-1"><p className="text-lg font-extrabold">{roomName(room)}</p><p className="text-sm text-[#776b71]">{buildingNames[room.building]} · {room.capacity} мест</p></div><button onClick={() => remove(room)} className="grid size-10 place-items-center rounded-xl text-[#9a8c93] opacity-100 transition hover:bg-[#ffe0e6] hover:text-[#a42f52] sm:opacity-0 sm:group-hover:opacity-100" aria-label={`Удалить ${roomName(room)}`}><Trash2 className="size-5" /></button></article>)}</div>
        {!rooms.length && <div className="mt-6"><EmptyState icon={Building2} title="Нет активных аудиторий" text="Добавь первую аудиторию в форме справа." /></div>}
      </section>
      <form onSubmit={submit} className="panel h-fit p-5 sm:p-7"><div className="mb-6 grid size-12 place-items-center rounded-2xl bg-[#ff5d8f] text-white"><Plus className="size-6" /></div><h3 className="text-xl font-extrabold">Добавить комнату</h3><p className="mt-1 text-sm leading-6 text-[#776b71]">Она сразу появится в поиске доступных.</p>
        <div className="mt-6 space-y-4"><label><span className="label">Корпус</span><select className="field" value={form.building} onChange={(e) => setForm({ ...form, building: e.target.value })}><option value="GK">Главный корпус</option><option value="LOMO">Корпус Ломоносова</option></select></label><label><span className="label">Номер</span><input className="field" type="number" min="1" value={form.number} onChange={(e) => setForm({ ...form, number: e.target.value })} placeholder="302" required /></label><label><span className="label">Вместимость</span><input className="field" type="number" min="1" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} placeholder="24" required /></label></div>
        <button disabled={busy} className="btn-primary mt-6 w-full">{busy ? <LoaderCircle className="size-5 animate-spin" /> : <Plus className="size-5" />} Добавить</button>
      </form>
    </div>
  )
}

export default function App() {
  const isAdmin = window.location.pathname.startsWith('/admin')
  const [view, setView] = useState(isAdmin ? 'approvals' : 'booking')
  const [rooms, setRooms] = useState([])
  const [pendingCount, setPendingCount] = useState(0)
  const [popularTags, setPopularTags] = useState([])
  const [loading, setLoading] = useState(true)
  const [toast, setToast] = useState(null)
  const [userId, setUserId] = useState(() => {
    const stored = localStorage.getItem('roomie-user-id')
    return stored && /^\d+$/.test(stored) ? stored : DEFAULT_USER_ID
  })

  const notify = useCallback((message, type = 'success') => {
    setToast({ message, type })
    window.setTimeout(() => setToast(null), 4200)
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const pendingRequest = isAdmin || /^[1-9]\d*$/.test(userId)
        ? api.requests.search({ status: 'PENDING', userId: isAdmin ? undefined : userId, page: 0, size: 1 })
        : Promise.resolve({ totalElements: 0 })
      const [roomData, pendingData, tagData] = await Promise.all([api.rooms.all(), pendingRequest, isAdmin ? api.requests.popularTags() : Promise.resolve([])])
      setRooms(roomData); setPendingCount(pendingData.totalElements)
      setPopularTags(tagData)
    } catch (error) { notify(`${error.message}. Проверь, запущен ли бэкенд.`, 'error') }
    finally { setLoading(false) }
  }, [notify, isAdmin, userId])

  useEffect(() => { refresh() }, [refresh])
  useEffect(() => { localStorage.setItem('roomie-user-id', userId) }, [userId])
  useEffect(() => {
    document.title = isAdmin ? 'Roomie Admin — подтверждение заявок' : 'Roomie — бронирование аудиторий'
  }, [isAdmin])

  const nav = isAdmin
    ? [
        { id: 'approvals', label: 'Подтверждение', icon: ShieldCheck, count: pendingCount },
        { id: 'rooms', label: 'Аудитории', icon: DoorOpen },
        { id: 'rules', label: 'Правила', icon: LayoutDashboard },
      ]
    : [
        { id: 'booking', label: 'Забронировать', icon: Sparkles },
        { id: 'requests', label: 'Мои заявки', icon: LayoutDashboard, count: pendingCount },
      ]

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-[#241b20]/10 bg-[#fffaf5]/85 backdrop-blur-xl">
        <div className="mx-auto flex max-w-[1440px] items-center gap-4 px-4 py-3 sm:px-7">
          <a href={isAdmin ? '/admin' : '/'} className="mr-auto flex items-center gap-3"><span className={`grid size-10 place-items-center rounded-[14px] border-2 border-[#241b20] text-white shadow-[3px_3px_0_#241b20] ${isAdmin ? 'bg-[#241b20]' : 'bg-[#ff5d8f]'}`}>{isAdmin ? <ShieldCheck className="size-5" /> : <DoorOpen className="size-5" />}</span><span className="font-display text-lg font-extrabold">roomie{isAdmin && <span className="text-[#ff477e]"> admin</span>}</span></a>
          <nav className="hidden items-center gap-1 rounded-2xl bg-white p-1.5 shadow-sm md:flex">{nav.map(({ id, label, icon: Icon, count }) => <button key={id} onClick={() => setView(id)} className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-extrabold transition ${view === id ? 'bg-[#241b20] text-white' : 'text-[#655960] hover:bg-[#f7eef1]'}`}><Icon className="size-4" />{label}{count > 0 && <span className="grid min-w-5 place-items-center rounded-full bg-[#ff5d8f] px-1.5 py-0.5 text-[11px] text-white">{count}</span>}</button>)}</nav>
          {isAdmin ? <a href="/" className="btn-soft !min-h-10 !px-3"><DoorOpen className="size-4" /><span className="hidden sm:inline">К пользователю</span></a> : <><a href="/admin" className="btn-soft !min-h-10 !px-3"><ShieldCheck className="size-4" /><span className="hidden sm:inline">Админка</span></a><label className="hidden max-w-[170px] lg:block"><span className="sr-only">ID пользователя</span><input type="number" min="1" className="field !rounded-xl !px-3 !py-2 text-xs" value={userId} onChange={(e) => setUserId(e.target.value)} title="ID текущего пользователя" /></label></>}
        </div>
      </header>

      <main className="mx-auto max-w-[1440px] px-4 py-7 sm:px-7 sm:py-10">
        <div className="mb-7 flex items-center justify-between gap-4"><div><p className="text-sm font-bold text-[#776b71]">{isAdmin ? 'Административная панель · без проверки ролей' : new Intl.DateTimeFormat('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())}</p><h1 className="mt-1 font-display text-2xl font-extrabold sm:text-4xl">{view === 'booking' ? 'Как насчёт встречи?' : view === 'requests' ? 'Мои заявки' : view === 'approvals' ? 'Подтверждение заявок' : view === 'rules' ? 'Правила обработки' : 'Управление местами'}</h1></div>{loading && <LoaderCircle className="size-6 animate-spin text-[#ff477e]" />}</div>
        {!isAdmin && <label className="mb-5 block lg:hidden"><span className="label">ID пользователя</span><input type="number" min="1" className="field max-w-48" value={userId} onChange={(e) => setUserId(e.target.value)} /></label>}
        {view === 'booking' && <BookingView rooms={rooms} userId={userId} onCreated={refresh} notify={notify} />}
        {view === 'requests' && <RequestsView rooms={rooms} loading={loading} onRefresh={refresh} onAction={refresh} notify={notify} userId={userId} />}
        {view === 'approvals' && <RequestsView rooms={rooms} loading={loading} onRefresh={refresh} onAction={refresh} notify={notify} canReview popularTags={popularTags} />}
        {view === 'rooms' && <RoomsView rooms={rooms} onRefresh={refresh} notify={notify} />}
        {view === 'rules' && isAdmin && <RulesView notify={notify} />}
      </main>

      <nav className="fixed bottom-3 left-1/2 z-40 flex w-[calc(100%-1.5rem)] -translate-x-1/2 items-center justify-around rounded-2xl border border-[#241b20]/10 bg-white/95 p-2 shadow-card backdrop-blur md:hidden">{nav.map(({ id, label, icon: Icon, count }) => <button key={id} onClick={() => setView(id)} className={`relative flex min-w-20 flex-col items-center gap-1 rounded-xl px-3 py-2 text-[11px] font-extrabold ${view === id ? 'bg-[#241b20] text-white' : 'text-[#776b71]'}`}><Icon className="size-5" />{label}{count > 0 && <span className="absolute right-2 top-1 grid min-w-4 place-items-center rounded-full bg-[#ff5d8f] px-1 text-[9px] text-white">{count}</span>}</button>)}</nav>
      <Toast toast={toast} onClose={() => setToast(null)} />
      <div className="h-24 md:hidden" />
    </div>
  )
}
