MAX_SAVED = 50


def upsert_gradient(saved, name, stops):
    entry = {"name": name, "stops": [list(stop) for stop in stops]}
    result = [g for g in saved if g.get("name") != name]
    result.append(entry)
    if len(result) > MAX_SAVED:
        result = result[len(result) - MAX_SAVED :]
    return result


def remove_gradient(saved, name):
    return [g for g in saved if g.get("name") != name]
